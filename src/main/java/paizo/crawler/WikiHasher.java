package paizo.crawler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;

import com.google.common.collect.Lists;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		hash(true, "https://pathfinderwiki.com/wiki/Special:ListFiles?limit=100");
		/*hash(false,
			"https://pathfinderwiki.com/wiki/Special:ListFiles?limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20121006205929&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20191106205929&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20180619211405&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20170519060003&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20160715135515&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20150811040511&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20141115001824&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20140326134233&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20130916235453&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20120730193747&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20110328010531&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20101107020746&limit=500",
			"https://pathfinderwiki.com/mediawiki/index.php?title=Special:ListFiles&offset=20101103210808&limit=500"
			
		);*/
	}

	private static void hash(boolean replace, String... urls) throws Exception {
		File file = new File("meta/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, HashedImage[].class));
		
		MyPool pool = new MyPool("wiki hashing");
		
		for(String url : urls) {
			var doc = Jsoup.connect(url).maxBodySize(0).get();
			for(var e:doc.getElementsByClass("image")) {
				String name = e.attr("href").substring(6);
				String imgUrl = StringUtil.resolve(doc.baseUri(), e.getElementsByTag("img").attr("src"));
				imgUrl = imgUrl.substring(0, imgUrl.lastIndexOf('/')).replace("/thumb/", "/");
				pool.submit(new WikiHasher(replace, known, name, imgUrl));
			}
		}
		
		pool.shutdown();
		known.sort(Comparator.comparing(HashedImage::getName));
		Jackson.MAPPER.writeValue(file, known);
	}
	
	private final boolean replace;
	private final List<HashedImage> known;
	private final String name;
	private final String url;

	@Override
	public Void call() throws Exception {
		if(!replace) {
			synchronized (known) {
				if(known.stream().anyMatch(k->k.getName().equals(name)))
					return null;
			}
		}
		
		byte[] bytes = IOUtils.toByteArray(new URL(url));
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
		
		HashedImage hi = new HashedImage();
		hi.setName(name);
		hi.setUrl(url);
		hi.setHash(hash(img));
		synchronized (known) {
			if(replace) {
				known.removeIf(i->i.getName().equals(name));
			}
			else {
				synchronized (known) {
					if(known.stream().anyMatch(k->k.getName().equals(name)))
						return null;
				}
			}
			known.add(hi);
		}
		
		return null;
	}
	
	public static Hash hash(BufferedImage img) {
		return HASHER.hash(img);
	}
	
	private static final HashingAlgorithm HASHER = new PerceptiveHash(128);
	public static boolean similarHashes(Hash h1, Hash h2) {
		return h1.normalizedHammingDistance(h2)<0.1;
	}
}
