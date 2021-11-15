import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.io.FileUtils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImageDownloader implements PBICallable {
	
	private static final Map<String, String> IMAGES = new HashMap<>();
	public static void main(String[] args) throws InterruptedException, IOException {
		var pool = (ForkJoinPool)Executors.newWorkStealingPool();
		for(var f:new File("blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null)
				continue;
			
			for(BlogImage img : post.getImages()) {
				String old = IMAGES.put(img.getName(), img.getFullPath());
				if(old == null) {
					pool.submit(new ImageDownloader(img));
				}
				else if(!old.equals(img.getFullPath())) {
					System.err.println("same name "+img.getName()+" for:\n\t"+img.getFullPath()+"\n\t"+old);
				}
			}
			
		}
		pool.shutdown();
		while(!pool.isTerminated()) {
			Thread.sleep(1000);
			System.out.println(pool.getQueuedSubmissionCount()+" remaining");
		}
	}
	
	private final BlogImage img;

	@Override
	public String getBlogId() {
		return img.getName();
	}

	@Override
	public void run() throws Exception {
		File target = new File(new File("blog_post_images"), img.getName());
		target.getParentFile().mkdirs();
		FileUtils.copyURLToFile(new URL(img.getFullPath()), target);
	}

}
