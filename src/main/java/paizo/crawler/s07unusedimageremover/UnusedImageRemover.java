package paizo.crawler.s07unusedimageremover;
import java.io.File;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.model.BlogPost;

@RequiredArgsConstructor
public class UnusedImageRemover {

	public static void main(String... args) throws Exception {
	    var posts = new ArrayList<BlogPost>();
		for(var f:new File("blog_posts_details").listFiles()) {
		    var post = Jackson.MAPPER.readValue(f, BlogPost.class);
		    if(post.getImages() != null)
		        posts.add(post);
		}

		for(var f:new File("blog_post_images").listFiles()) {
		    var used = posts.stream().flatMap(p->p.getImages().stream()).anyMatch(img->img.getName().equals(f.getName()));
		    if(!used) {
		        System.out.println("Removed unused image "+f.getName());
		        FileUtils.delete(f);
		    }
		}
	}
}
