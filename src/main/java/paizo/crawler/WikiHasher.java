package paizo.crawler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
	    hash(false,
	        "https://pathfinderwiki.com/w/api.php?action=query&format=json&list=allimages&utf8=1&formatversion=2&aisort=timestamp&aiprop=url&ailimit=5000",
	        "https://starfinderwiki.com/w/api.php?action=query&format=json&list=allimages&utf8=1&formatversion=2&aisort=timestamp&aiprop=url&ailimit=5000"
	    );
	}

	private static void hash(boolean replace, String... urls) throws Exception {
		File file = new File("meta/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, HashedImage[].class));

		MyPool pool = new MyPool("wiki hashing");

		for(String url : urls) {
		    String aicontinue = null;
		    List<JsonNode> all = new ArrayList<>();
		    do {
		        ObjectNode result = (ObjectNode) new ObjectMapper().readTree(new URL(url+(aicontinue==null?"":("&aicontinue="+URLEncoder.encode(aicontinue)))));

    		    aicontinue = result.path("continue").path("aicontinue").asText();
    		    ((ArrayNode)(result.get("query").get("allimages"))).elements().forEachRemaining(all::add);
		    } while(!aicontinue.isEmpty());

		    for(var n:all) {
		        String name = n.get("name").asText();
		        String imgUrl = n.get("url").asText();
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
				if(known.stream().anyMatch(k->k.getUrl().equals(url)))
					return null;
			}
		}

		if(name.endsWith(".svg") || name.endsWith(".mp3")) {
		    return null;
		}

		Uninterruptibles.sleepUninterruptibly((int)(Math.random()*20), TimeUnit.SECONDS);

		byte[] bytes = IOUtils.toByteArray(new URL(url));
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
		if(img == null) {
		    System.err.println("\tCould not parse "+url);
		    return null;
		}
		img = Cropper.crop(img);

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
