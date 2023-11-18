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
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.WikiImage;
import paizo.crawler.s03articledetailsextractor.ArticleDetailsExtractor;
import paizo.crawler.s08wikihasher.WikiHasher;

@RequiredArgsConstructor
public class BlogHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		File file = new File("meta/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, WikiImage[].class));
		known.removeIf(i->i.getHash() == null);
		
		var pool = new MyPool("Blog Hasher");
		for(var f:new File("blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null)
				continue;
			
			pool.submit(new BlogHasher(known, post));
		}
		pool.shutdown();
	}
	
	private final List<WikiImage> known;
	private final BlogPost post;

	@Override
	public Void call() throws Exception {
		boolean changed = false;
		
		for(BlogImage img:post.getImages()) {
			
			if(img.getHash() == null && img.getLocalFile() != null) {
				File f = img.getLocalFileAsFile();
				if(f.exists()) {
					var raw = ImmutableImage.loader().fromFile(f);
					if(raw != null) {
						img.setHash(WikiHasher.hash(raw.awt()));
						img.setPixels(raw.awt().getWidth()*raw.awt().getHeight());
						changed = true;
					}
				}
			}
			
			if(img.getHash() == null) {
				continue;
			}
			
			var matches = known.stream()
				.filter(hi->WikiHasher.similarHashes(hi.getHash(), img.getHash()))
				.collect(Collectors.groupingBy(hi->hi.getWiki(), Collectors.toList()));
			
			if(!matches.isEmpty()) {
				if(matches.containsKey("pf") && img.getWikiMappings().getPf() == null) {
					changed = true;
					img.getWikiMappings().setPf(matches.get("pf").get(0));
				}
				if(matches.containsKey("sf") && img.getWikiMappings().getSf() == null) {
					changed = true;
					img.getWikiMappings().setSf(matches.get("sf").get(0));
				}
			}
		}
		
		if(changed) {
			ArticleDetailsExtractor.moveTimeZone(post);
			Jackson.MAPPER.writeValue(
				post.detailsFile(),
				post
			);
		}
		
		return null;
	}
}
