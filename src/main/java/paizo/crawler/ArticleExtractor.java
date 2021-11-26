package paizo.crawler;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ArticleExtractor implements PBICallable {
	
	private static final Pattern LINK = Pattern.compile("^https://paizo\\.com/community/blog/([0-9a-z]{5,})(\\?.*)?");
	
	public static void main(String... args) throws IOException, InterruptedException {
		run(new File("blog").listFiles());
	}
	
	public static void run(File[] files) throws IOException, InterruptedException {
		var pool = new MyPool("Article Extractor");
		var links = Collections.synchronizedSet(new HashSet<String>());
		for(var f:files) {
			System.out.println("Blog extractor parsing "+f);
			var doc = Jsoup.parse(f, StandardCharsets.UTF_8.name());
			doc.getElementsByAttributeValueMatching("href", LINK)
				.stream()
				.map(e->e.attr("href"))
				.map(LINK::matcher)
				.filter(Matcher::matches)
				.map(m->m.group(1))
				.distinct()
				.forEach(l-> {
					if(links.add(l))
						pool.submit(new ArticleExtractor(l));
				});
			System.out.println("Collected articles from "+f);
		}
		
		System.out.println("Waiting for queue");
		pool.shutdown();
	}
	
	private final String blogId;

	@Override
	public void run() throws Exception {
		File target = new File("blog_posts/"+blogId+".yaml");
		if(target.exists())
			return;
		
		String url = "https://paizo.com/community/blog/"+blogId;
		var doc = Jsoup.connect(url).maxBodySize(0).get();
		HTMLCleaner.clean(doc);
		var post = doc.getElementsByAttributeValue("itemtype", "http://schema.org/BlogPosting").get(0);
		post.getElementsByAttributeValue("itemprop", "comment").remove();
		
		
		BlogPost blogPost = new BlogPost();
		blogPost.setId(blogId);
		blogPost.setHtml(post);
		
		target.getParentFile().mkdirs();
		Jackson.BLOG_WRITER.writeValue(target, blogPost);
	}
}
