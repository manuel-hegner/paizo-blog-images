import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlogCollector implements Callable<Void> {
	
	private static ExecutorService POOL = Executors.newWorkStealingPool();
	private static AtomicInteger COUNTER = new AtomicInteger(0);
	
	public static void main(String[] args) throws InterruptedException {
		start(Integer.MAX_VALUE);
	}
	
	public static void start(int maxDepth) throws InterruptedException {
		COUNTER.incrementAndGet();
		POOL.submit(new BlogCollector(maxDepth, "https://paizo.com/community/blog", "current"));
		while(COUNTER.get()>0) {
			Thread.sleep(100);
		}
		POOL.shutdown();
		POOL.awaitTermination(1, TimeUnit.HOURS);
	}

	private final int maxDepth;
	private final String url;
	private final String name;
	
	private static final Pattern OLDER = Pattern.compile("<a href=\"([^\"]+)\" title=\"[^\"]+\">&lt;&lt; Older posts</a>");
	
	@Override
	public Void call() throws Exception {
		if(maxDepth<0)
			return null;
		try {
			var resp = Jsoup.connect(url)
				.timeout((int)TimeUnit.MINUTES.toMillis(5))
				.maxBodySize(0)
				.execute();

			if(resp.statusCode()<200 || resp.statusCode()>299)
				throw new IllegalStateException(resp.statusCode()+"\t"+resp.statusMessage());
			
			var raw = resp.body();
			
			var m = OLDER.matcher(raw);
			if(m.find()) {
				String link = m.group(1);
				if(link.startsWith("https://paizo.com/community/blog/")) {
					COUNTER.incrementAndGet();
					POOL.submit(new BlogCollector(maxDepth-1, link, StringUtils.removeStart(link, "https://paizo.com/community/blog/")));
				}
				else {
					System.err.println("Unknown "+url);
				}
			}
			
			File target = new File("blog/"+name.replaceAll("[^a-zA-Z0-9]", "_")+".html");
			target.getParentFile().mkdirs();
			Files.writeString(target.toPath(), raw);
			System.out.println("Written "+target);
			return null;
		} catch (Exception e) {
			System.err.println("Failed "+url);
			e.printStackTrace();
			throw e;
		} finally {
			COUNTER.decrementAndGet();
		}
	}
}
