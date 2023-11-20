package paizo.crawler.s08wikihasher;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.sksamuel.scrimage.ImmutableImage;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import lombok.RequiredArgsConstructor;
import paizo.crawler.common.ImageHelper;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.model.WikiImage;
import paizo.crawler.s06imageoptimizer.Cropper;

@RequiredArgsConstructor
public class WikiHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
	    hash(false,
	        new Target("pf", "https://pathfinderwiki.com/w/api.php?action=query&format=json&list=allimages&utf8=1&formatversion=2&aisort=timestamp&aidir=older&aiprop=url&ailimit=5000"),
	        new Target("sf", "https://starfinderwiki.com/w/api.php?action=query&format=json&list=allimages&utf8=1&formatversion=2&aisort=timestamp&aidir=older&aiprop=url&ailimit=5000")
	    );
	}
	
	private static record Target(String wiki, String url) {}

	@SafeVarargs
	private static void hash(boolean replace, Target... targets) throws Exception {
		File file = new File("data/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, WikiImage[].class));
		var foundUrls = new HashSet<>();

		
		var tasks = new ArrayList<WikiHasher>();
		for(var target : targets) {
		    String aicontinue = null;
		    List<JsonNode> all = new ArrayList<>();
		    do {
		        ObjectNode result = (ObjectNode) new ObjectMapper().readTree(new URL(target.url+(aicontinue==null?"":("&aicontinue="+URLEncoder.encode(aicontinue)))));

    		    aicontinue = result.path("continue").path("aicontinue").asText();
    		    ((ArrayNode)(result.get("query").get("allimages"))).elements().forEachRemaining(all::add);
		    } while(!aicontinue.isEmpty());

		    for(var n:all) {
		        String name = n.get("name").asText();
		        String imgUrl = n.get("url").asText();
		        foundUrls.add(imgUrl);
		        tasks.add(new WikiHasher(replace, target.wiki, known, name, imgUrl));
		    }
		}
		
		//remove outdated entries
		known.removeIf(img->!foundUrls.contains(img.getUrl()));
		var knownUrls = Sets.newConcurrentHashSet(known.stream().map(img->img.getUrl()).toList());
		
		MyPool pool = new MyPool("wiki hashing");
		tasks.forEach(t->{
			t.knownUrls=knownUrls;
			pool.submit(t);
		});
		pool.shutdown();
		known.sort(Comparator.comparing(WikiImage::getName));
		Jackson.MAPPER.writeValue(file, known);
	}

	private final boolean replace;
	private final String wiki;
	private final List<WikiImage> known;
	private Set<String> knownUrls;
	private final String name;
	private final String url;

	@Override
	public Void call() throws Exception {
		if(!replace && knownUrls.contains(url)) {
				return null;
		}

		if(name.endsWith(".svg") || name.endsWith(".mp3")) {
		    return null;
		}

		Uninterruptibles.sleepUninterruptibly((int)(Math.random()*20), TimeUnit.SECONDS);

		byte[] bytes = IOUtils.toByteArray(new URL(url));
		BufferedImage img = ImmutableImage.loader().fromBytes(bytes).awt();
		if(img == null) {
		    System.err.println("\tCould not parse "+url);
		    return null;
		}
		img = Cropper.crop(img);

		WikiImage hi = new WikiImage();
		hi.setWiki(wiki);
		hi.setName(name);
		hi.setUrl(url);
		hi.setUsesTransparency(ImageHelper.usesTransparency(img));
		hi.setHash(hash(img));
		hi.setPixels((long)img.getWidth()*img.getHeight());
		synchronized (known) {
			if(replace) {
				known.removeIf(i->i.getName().equals(name));
			}
			known.add(hi);
			knownUrls.add(hi.getUrl());
		}

		return null;
	}

	public static Hash hash(BufferedImage img) {
		return HASHER.hash(ImageHelper.normalizeForHashing(img));
	}

	private static final HashingAlgorithm HASHER = new PerceptiveHash(128);
	public static boolean similarHashes(Hash h1, Hash h2) {
		return h1.normalizedHammingDistance(h2)<0.1;
	}
}
