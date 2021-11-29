package paizo.crawler;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;

import com.google.common.collect.Lists;

import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		/*BufferedImage wiki = ImageIO.read(new File("180px-Bosk.jpg"));
		BufferedImage paizo = ImageIO.read(new File("blog_post_images/20180718-Bosk.jpg"));
		
		byte[] hW = hash(wiki);
		byte[] hP = hash(paizo);
		
		System.out.println(Base64.getEncoder().encodeToString(hW));
		System.out.println(Base64.getEncoder().encodeToString(hP));
		
		System.exit(0);*/
		hash(
			"https://pathfinderwiki.com/wiki/Special:ListFiles?limit=100"/*,
			"https://pathfinderwiki.com/wiki/Special:ListFiles?limit=500",
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
			*/
		);
	}

	private static void hash(String... urls) throws Exception {
		File file = new File("wiki/hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, HashedImage[].class));
		
		MyPool pool = new MyPool("wiki hashing");
		
		for(String url : urls) {
			var doc = Jsoup.connect(url).maxBodySize(0).get();
			for(var e:doc.getElementsByClass("image")) {
				String name = e.attr("href").substring(6);
				pool.submit(new WikiHasher(known, name, StringUtil.resolve(doc.baseUri(), e.getElementsByTag("img").attr("src"))));
			}
		}
		
		pool.shutdown();
		Jackson.MAPPER.writeValue(file, known);
	}
	
	private final List<HashedImage> known;
	private final String name;
	private final String url;

	@Override
	public Void call() throws Exception {
		synchronized (known) {
			if(known.stream().anyMatch(i->i.getName().equals(name)))
				return null;
		}
		try(InputStream in = new URL(url).openStream()) {
			BufferedImage img = ImageIO.read(in);
			
			HashedImage hi = new HashedImage();
			hi.setName(name);
			hi.setUrl(url);
			hi.setHash(hash(img));
			synchronized (known) {
				if(known.stream().anyMatch(i->i.getName().equals(name)))
					return null;
				known.add(hi);
			}
		}
		
		return null;
	}
	
	public static String hash(BufferedImage img) {
		HashingAlgorithm hasher = new PerceptiveHash(64);
		return Base64.getEncoder().encodeToString(hasher.hash(img).toByteArray());
	}
}
