package paizo.crawler;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import com.google.common.collect.Lists;
 
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlogHasher implements Callable<Void> {
	public static void main(String... args) throws Exception {
		File file = new File("meta/wiki_hashes.yaml");
		var known = Lists.newArrayList(Jackson.MAPPER
				.readValue(file, HashedImage[].class));
		
		var pool = new MyPool("Blog Hasher");
		for(var f:new File("blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null)
				continue;
			
			if(post.getImages().stream().anyMatch(i->i.getHash()==null || i.getWikiImage()==null)) {
				pool.submit(new BlogHasher(known, post));
			}
		}
		pool.shutdown();
	}
	
	private final List<HashedImage> known;
	private final BlogPost post;

	@Override
	public Void call() throws Exception {
		boolean changed = false;
		
		for(BlogImage img:post.getImages()) {
			
			if(img.getHash() == null) {
				File f = new File("blog_post_images/"+img.getName());
				if(f.exists()) {
					var raw = ImageIO.read(f);
					if(raw != null) {
						img.setHash(WikiHasher.hash(raw));
						changed = true;
					}
				}
			}
			
			if(img.getWikiImage() == null && img.getHash() != null) {
				var match = known.stream()
					.filter(hi->WikiHasher.similarHashes(hi.getHash(), img.getHash()))
					.findAny();
				
				if(match.isPresent()) {
					img.setWikiImage(match.get().getName());
					changed = true;
				}
			}
			
		}
		
		if(changed) {
			ArticleDetailsExtractor.moveTimeZone(post);
			Jackson.MAPPER.writeValue(
				new File("blog_posts_details/"+post.getId()+".yaml"),
				post
			);
		}
		
		return null;
	}
}
