package paizo.crawler;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImageDownloader implements PBICallable {
	
	private static final Map<String, String> IMAGES = new HashMap<>();
	public static void main(String... args) throws InterruptedException, IOException {
		var pool = new MyPool("Image Downloader");
		for(var f:new File("blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null)
				continue;
			
			for(BlogImage img : post.getImages()) {
				String old = IMAGES.put(img.getName(), img.getFullPath());
				if(old == null) {
					File target = Path.of(
							"blog_post_images",
							img.getName()
					).toFile();
					
					if(!target.exists()) {
						pool.submit(new ImageDownloader(post, img, target));
					}
				}
				else if(!old.equals(img.getFullPath())) {
					//System.err.println("same name "+img.getName()+" for:\n\t"+img.getFullPath()+"\n\t"+old);
				}
			}
			
		}
		pool.shutdown();
	}
	
	private final BlogPost post;
	private final BlogImage img;
	private final File target;

	@Override
	public String getBlogId() {
		return img.getName();
	}

	private final static DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
	@Override
	public void run() throws Exception {
		if(!target.exists()) {
			target.getParentFile().mkdirs();
			FileUtils.copyURLToFile(new URL(img.getFullPath()), target);
		}
	}
	
	public static String wikitext(BlogPost post, BlogImage img) {
		return "== Summary ==\n"
				+ "\n"
				+ "{{File\n"
				+ "| year     = "+(post.getDate()==null?"":post.getDate().getYear())+"\n"
				+ "| copy     = Paizo Inc.\n"
				+ "| artist   = \n"
				+ "| print    = \n"
				+ "| page     = \n"
				+ "| web      = {{Cite web\n"
				+ "  | author = \n"
				+ "  | date   = "+(post.getDate()==null?"":FORMAT.format(post.getDate()))+"\n"
				+ "  | title  = "+post.getTitle()+"\n"
				+ "  | page   = Paizo Blog\n"
				+ "  | url    = https://paizo.com/community/blog/"+post.getId()+"\n"
				+ "  }}   \n"
				+ "| summary  = "+img.getAlt()+"\n"
				+ "| keyword1 = \n"
				+ "| keyword2 = \n"
				+ "}}   \n"
				+ "\n"
				+ "== Licensing ==\n"
				+ "\n"
				+ "{{Paizo CUP|blog|url=https://paizo.com/community/blog/"+post.getId()+"}}";
	}
}
