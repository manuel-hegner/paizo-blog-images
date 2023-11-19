package paizo.crawler.s04duplicateimageremover;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.model.BlogPost;

/* Removes any image that appears after already appearing in a previous post */
@RequiredArgsConstructor
public class DuplicateImageRemover {

	public static void main(String... args) throws Exception {
	    var posts = new ArrayList<BlogPost>();
		for(var f:new File("data/blog_posts_details").listFiles()) {
		    var post = Jackson.MAPPER.readValue(f, BlogPost.class);
		    posts.add(post);
		}
		posts.sort(Comparator.comparing(BlogPost::getDate));

		for(int i=0;i<posts.size();i++) {
		    var post = posts.get(i);

		    if(post.getImages() == null) continue;

		    boolean removed = false;
		    for(int j=0;j<i;j++) {
		        var before = posts.get(j);

		        if(before.getImages() != null) {
    		        removed|=post.getImages().removeIf(img ->
    		            before.getImages().stream().anyMatch(befImg -> befImg.getFullPath().equals(img.getFullPath()))
    		        );
		        }
		    }
		    if(removed) {
                System.out.println("Removed image(s) from "+post.getId());
                Jackson.MAPPER.writeValue(post.detailsFile(), post);
            }
		}
	}
}
