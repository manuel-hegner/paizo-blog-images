package paizo.crawler.s09bloghasher;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.sksamuel.scrimage.ImmutableImage;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.model.ImageInfo;
import paizo.crawler.common.model.WikiImage;
import paizo.crawler.s08wikihasher.WikiHasher;

@RequiredArgsConstructor
public class BlogHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		File file = new File("data/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER.readValue(file, WikiImage[].class));
		known.removeIf(i -> i.getHash() == null);

		var pool = new MyPool("Blog Hasher");
		for (var d : new File("data/images").listFiles()) {
			for(var f : d.listFiles()) {
				var img = Jackson.MAPPER.readValue(new File(f, "info.yaml"), ImageInfo.class);
	
				if(img.getOptimizedFile()!=null && img.getOptimizedFile().isFile())
					pool.submit(new BlogHasher(known, img));
			}
		}
		pool.shutdown();
	}

	private final List<WikiImage> known;
	private final ImageInfo image;

	@Override
	public Void call() throws Exception {
		boolean changed = false;

		if (image.getHash() == null) {
			File f = image.getOptimizedFile();
			if (f.exists()) {
				var raw = ImmutableImage.loader().fromFile(f);
				if (raw != null) {
					image.setHash(WikiHasher.hash(raw.awt()));
					image.setPixels(raw.awt().getWidth() * raw.awt().getHeight());
					changed = true;
				}
			}
		}

		if (image.getHash() == null) {
			return null;
		}

		var matches = known.stream().filter(hi -> WikiHasher.similarHashes(hi.getHash(), image.getHash()))
				.collect(Collectors.groupingBy(hi -> hi.getWiki(), Collectors.toList()));

		if (!matches.isEmpty()) {
			if (matches.containsKey("pf") && image.getWikiMappings().getPf() == null) {
				changed = true;
				image.getWikiMappings().setPf(matches.get("pf").get(0));
			}
			if (matches.containsKey("sf") && image.getWikiMappings().getSf() == null) {
				changed = true;
				image.getWikiMappings().setSf(matches.get("sf").get(0));
			}
		}

		if (changed) {
			Jackson.MAPPER.writeValue(image.getInfoFile(), image);
		}

		return null;
	}
}
