package paizo.crawler.s04duplicateimageremover;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.model.BlogPost;

/* Removes any image that appears after already appearing in a previous post */
@RequiredArgsConstructor
public class DuplicateImageRemover {

	public static void main(String... args) throws Exception {
	    var posts = new ArrayList<BlogPost>();
	    for(var d:new File("data/blog_posts_details").listFiles()) {
	    	for(var f:d.listFiles()) {
			    var post = Jackson.MAPPER.readValue(f, BlogPost.class);
			    posts.add(post);
	    	}
		}
		posts.sort(Comparator.comparing(BlogPost::getDate));
		
		Set<String> knownImages = new HashSet<>();

		for(int i=0;i<posts.size();i++) {
		    var post = posts.get(i);

		    if(post.getImages() == null) continue;

		    AtomicBoolean removed = new AtomicBoolean(false);
		    post.getImages().removeIf(img-> {
		    	var anyRem = img.getCandidatePaths().removeIf(c->!knownImages.add(c));
		    	removed.set(removed.get()||anyRem);
		    	return img.getCandidatePaths().isEmpty();
		    });

		    if(removed.get()) {
                System.out.println("Removed image(s) from "+post.getId());
                Jackson.MAPPER.writeValue(post.detailsFile(), post);
            }
		}
	}
}
