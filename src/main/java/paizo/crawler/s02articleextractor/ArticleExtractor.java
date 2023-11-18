package paizo.crawler.s02articleextractor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import paizo.crawler.common.HTMLCleaner;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.BlogPost;

@Getter
@RequiredArgsConstructor
public class ArticleExtractor implements PBICallable {
	
	private static final Pattern LINK = Pattern.compile("^https://paizo\\.com/community/blog/([0-9a-z]{5,})(\\?.*)?$");
	
	public static void main(String... args) throws IOException, InterruptedException {
		run(new File("blog").listFiles());
	}
	
	public static void run(File[] files) throws IOException, InterruptedException {
		var pool = new MyPool("Article Extractor");
		for(var f:files) {
			System.out.println("Blog extractor parsing "+f);
			var doc = Jsoup.parse(f, StandardCharsets.UTF_8.name());
			doc.getElementsByAttributeValue("itemprop", "articlebody").remove();
			doc.getElementsByAttributeValueContaining("style", "display: none").remove();
			doc.getElementsByAttributeValueMatching("href", LINK)
				.stream()
				.filter(e->"Link".equals(e.ownText()))
				.map(ArticleExtractor::findTime)
				.collect(Collectors.toMap(
					p->p.getKey(),
					Function.identity(),
					(a,b)->Pair.of(a.getLeft(), same(a.getRight(), b.getRight()))
				))
				.values()
				.forEach(p -> pool.submit(new ArticleExtractor(
						p.getLeft(),
						p.getRight()
				)));
			System.out.println("Collected articles from "+f);
		}
		
		System.out.println("Waiting for queue");
		pool.shutdown();
	}
	
	private static <T> T same(T a, T b) {
		if(a == null) {
			if(b == null)
				return null;
			else
				return b;
		}
		else {
			if(b == null)
				return a;
			else if(a.equals(b))
				return b;
			else
				throw new IllegalStateException("Unequal same "+a+" and "+b);
		}
	}
	
	private static Pair<String, ZonedDateTime> findTime(Element e) {
		try {
			var m = LINK.matcher(e.attr("href"));
			m.matches();
			String link = m.group(1);
			List<String> dates = firstParentWith(e, p->p.attr("itemtype").equals("http://schema.org/BlogPosting"))
				.stream()
				.flatMap(p->p.getElementsByTag("time").eachAttr("datetime").stream())
				.filter(StringUtils::isNotBlank)
				.map(StringUtils::strip)
				.distinct()
				.collect(Collectors.toList());
			
			if(dates.size() == 1) {
				return Pair.of(link, OffsetDateTime.parse(dates.get(0)).toZonedDateTime());
			}
			else {
				return Pair.of(link, null);
			}
		} catch(Exception ex) {
			throw new RuntimeException("Failed to get Link for "+e.attr("href"), ex);
		}
	}
	
	private static Optional<Element> firstParentWith(Element e, Predicate<Element> condition) {
		while((e=e.parent())!=null) {
			if(condition.test(e))
				return Optional.of(e);
		}
		return Optional.empty();
	}

	private final String blogId;
	private final ZonedDateTime date;

	@Override
	public void run() throws Exception {
		File target = new File("blog_posts/"+blogId+".yaml");
		
		if(target.exists() && (date==null || date.plusDays(30).isBefore(ZonedDateTime.now())))
			return;

		String url = "https://paizo.com/community/blog/"+blogId;
		var doc = Jsoup.connect(url).maxBodySize(0).get();
		HTMLCleaner.clean(doc);
		var post = doc.getElementsByAttributeValue("itemtype", "http://schema.org/BlogPosting").get(0);
		post.getElementsByAttributeValue("itemprop", "comment").remove();


		BlogPost blogPost = new BlogPost();
		blogPost.setId(blogId);
		blogPost.setHtml(post);
		blogPost.setDate(date);
		
		target.getParentFile().mkdirs();
		Jackson.BLOG_WRITER.writeValue(target, blogPost);
	}
}
