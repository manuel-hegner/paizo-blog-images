package paizo.crawler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;

import com.google.common.collect.Lists;

import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		/*BufferedImage wiki = ImageIO.read(new File("Crimson_throne.jpg"));
		BufferedImage paizo = ImageIO.read(new File("blog_post_images/PZO9000-2-CrimsonThrone.jpg"));
		
		String hW = hash(wiki);
		String hP = hash(paizo);
		
		System.out.println(hW);
		System.out.println(hP);
		
		byte[] bytes = IOUtils.toByteArray(new URL("https://pathfinderwiki.com/mediawiki/images/thumb/3/36/Crimson_throne.jpg/180px-Crimson_throne.jpg"));
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
		System.out.println(hash(img));
		
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
				String imgUrl = StringUtil.resolve(doc.baseUri(), e.getElementsByTag("img").attr("src"));
				imgUrl = imgUrl.substring(0, imgUrl.lastIndexOf('/')).replace("/thumb/", "/");
				pool.submit(new WikiHasher(known, name, imgUrl));
			}
		}
		
		pool.shutdown();
		known.sort(Comparator.comparing(HashedImage::getName));
		Jackson.MAPPER.writeValue(file, known);
	}
	
	private final List<HashedImage> known;
	private final String name;
	private final String url;

	@Override
	public Void call() throws Exception {
		byte[] bytes = IOUtils.toByteArray(new URL(url));
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
		
		HashedImage hi = new HashedImage();
		hi.setName(name);
		hi.setUrl(url);
		hi.setHash(hash(img));
		synchronized (known) {
			known.removeIf(i->i.getName().equals(name));
			known.add(hi);
		}
		
		return null;
	}
	
	public static String hash(BufferedImage img) {
		HashingAlgorithm hasher = new PerceptiveHash(128);
		return Base64.getEncoder().encodeToString(hasher.hash(img).toByteArray());
	}
}
