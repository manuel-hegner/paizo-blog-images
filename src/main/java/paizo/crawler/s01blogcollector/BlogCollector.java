package paizo.crawler.s01blogcollector;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;

import com.google.common.util.concurrent.Uninterruptibles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paizo.crawler.common.HTMLCleaner;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.model.BlogPost;

@Slf4j
@RequiredArgsConstructor
public class BlogCollector implements Callable<Void> {
	
	private static MyPool POOL = new MyPool("Blog Collector");
	
	public static void main(String[] args) throws InterruptedException {
		start(Integer.MAX_VALUE);
	}
	
	public static void start(int maxDepth) throws InterruptedException {
		var driver = new FirefoxDriver(
				new FirefoxOptions()
					.addArguments(List.of("-headless"))
		);
		driver.get("https://paizo.com/blog");
		
		Set<String> foundURLs = new HashSet<>();
		
		Instant lastAdded = Instant.now();
		for(int d=0;d<maxDepth;d++) {
			new Actions(driver).scrollByAmount(0, 2_000_000_000).perform();
			Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
			if(checkURLs(driver, foundURLs) > 0) {
				lastAdded = Instant.now();
			}
			
			if(Duration.between(lastAdded, Instant.now()).toSeconds()>60) {
				System.out.println("Found end");
				break;
			}
		}

		checkURLs(driver, foundURLs);
		driver.close();
		
		POOL.shutdown();
		
	}
	//Thu Nov 20 2025 18:00:00 GMT+0000
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("EEE MMM d yyyy H:mm:ss 'GMT'Z", Locale.US);
	private static int checkURLs(FirefoxDriver driver, Set<String> foundURLs) {
		var articles = driver.findElements(By.cssSelector("li[role=\"article\"] > a"));
		int c = 0;
		
		for(var art:articles) {
			var href = art.getAttribute("href");
			var time = art.findElement(By.tagName("time")).getAttribute("datetime");
			time = time.substring(0, time.indexOf('(')).trim();
			var instant = FORMAT.parse(time, ZonedDateTime::from);
			if(href.startsWith("/")) href = "https://paizo.com"+href;
			if(foundURLs.add(href)) {
				POOL.submit(new BlogCollector(href, instant));
				c++;
			}
		}
		System.out.println("Found "+c+" articles");
		return c;
	}

	private final String url;
	private final ZonedDateTime date;
	
	@Override
	public Void call() throws Exception {
		try {
			String id = date.toLocalDate()
					+"-"+
					StringUtils.removeStart(url, "https://paizo.com/blog/")
						.replaceAll("[^a-zA-Z0-9\\-]", "_");
			BlogPost blogPost = new BlogPost();
			blogPost.setId(id);
			blogPost.setUrl(url);
			blogPost.setDate(date);
			
			File target = blogPost.postFile();
			
			if(target.isFile()) return null;
			
			var resp = Jsoup.connect(url)
				.timeout((int)TimeUnit.MINUTES.toMillis(5))
				.maxBodySize(0)
				.execute();

			if(resp.statusCode()<200 || resp.statusCode()>299)
				throw new IllegalStateException(resp.statusCode()+"\t"+resp.statusMessage());
			
			var doc = resp.parse();
			HTMLCleaner.clean(doc);
			target.getParentFile().mkdirs();
			
			blogPost.setHtml(doc);
			blogPost.setReported(false);
			Jackson.BLOG_WRITER.writeValue(target, blogPost);
			System.out.println("Written "+target);
			
			
			return null;
		} catch (Exception e) {
			System.err.println("Failed "+url);
			e.printStackTrace();
			throw e;
		} finally {
		}
	}
}
