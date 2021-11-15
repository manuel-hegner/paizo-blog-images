import java.io.File;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArticleDetailsExtractor implements Callable<Void> {
	public static void main(String[] args) throws InterruptedException {
		var pool = (ForkJoinPool)Executors.newWorkStealingPool();
		for(var f:new File("blog_posts").listFiles()) {
			pool.submit(new ArticleDetailsExtractor(f));
		}
		pool.shutdown();
		while(!pool.isTerminated()) {
			Thread.sleep(1000);
			System.out.println(pool.getQueuedSubmissionCount()+" remaining");
		}
	}
	
	private final File file;

	@Override
	public Void call() throws Exception {
		BlogPost post = Jackson.BLOG_READER.readValue(file);
		
		findDate(post);
		
		post.setHtml(null);
		
		File target = new File(new File("blog_posts_details"), file.getName());
		target.getParentFile().mkdirs();
		Jackson.BLOG_WRITER.writeValue(target, post);
		
		return null;
	}

	private void findDate(BlogPost post) {
		// TODO Auto-generated method stub
		
	}
}
