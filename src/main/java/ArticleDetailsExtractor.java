import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArticleDetailsExtractor implements PBICallable {
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
	public String getBlogId() {
		return file.getName();
	}

	@Override
	public void run() throws Exception {
		BlogPost post = Jackson.BLOG_READER.readValue(file);
		
		findDate(post);
		post.setTitle(findTitle(post));
		post.setTags(findTags(post));
		post.setImages(findImages(post));
		
		post.setHtml(null);
		
		File target = new File(new File("blog_posts_details"), file.getName());
		target.getParentFile().mkdirs();
		Jackson.BLOG_WRITER.writeValue(target, post);
	}
	
	private List<BlogImage> findImages(BlogPost post) {
		var imgs = post.getHtml()
			.getElementsByAttributeValueStarting("src", "https://cdn.paizo.com/")
			.stream()
			.map(e->Pair.of(e.attr("src"), e.attr("alt")))
			.filter(p->StringUtils.isNotBlank(p.getKey()))
			.map(p-> {
				String l = p.getKey();
				if(l.contains("?"))
					l=l.substring(0,l.indexOf("?"));
				return Pair.of(l, p.getValue());
			})
			.map(p-> {
				BlogImage img = new BlogImage();
				String l = p.getKey();
				img.setFullPath(l);
				img.setName(l.substring(l.lastIndexOf("/")+1));
				img.setAlt(StringUtils.stripToNull(p.getValue().replaceAll("\s+", " ")));
				return img;
			})
			.collect(Collectors.toList());
		if(imgs.isEmpty())
			return null;
		return imgs;
	}

	private String[] findTags(BlogPost post) {
		String[] tags = post.getHtml()
			.getElementsByAttributeValueStarting("href", "https://paizo.com/community/blog/tags/")
			.stream()
			.map(e->e.attr("href"))
			.filter(StringUtils::isNotBlank)
			.map(l->StringUtils.removeStart(l, "https://paizo.com/community/blog/tags/"))
			.distinct()
			.sorted()
			.toArray(String[]::new);
		if(tags.length == 0)
			return null;
		return tags;
	}

	private String findTitle(BlogPost post) {
		var first = post.getHtml().getElementsByAttributeValue("itemprop", "headline").first();
		if (first != null)
			return first.text();
		first = post.getHtml().getElementsByTag("h1").first();
		if (first != null)
			return first.text();
		return null;
	}

	private static final ZoneId PST = ZoneId.of("PST", ZoneId.SHORT_IDS);
	private static final DateTimeFormatter FORMAT_FULL = DateTimeFormatter.ofPattern("MMMM dd['st']['nd']['th'], yyyy", Locale.US);
	private static final DateTimeFormatter FORMAT_SHORT = DateTimeFormatter.ofPattern("EE, MMM d, yyyy 'at' hh:mm a 'Pacific'", Locale.US);
	private String[] DAYS_FULL = Arrays.stream(DayOfWeek.values())
			.map(d->d.getDisplayName(TextStyle.FULL, Locale.US))
			.toArray(String[]::new);
	private void findDate(BlogPost post) {
		var dateElem = post.getHtml().getElementsByClass("date").first();
		if(dateElem == null)
			return;
		String date = dateElem.ownText();
		if(StringUtils.startsWithAny(date, DAYS_FULL)) {
			try {
				for(String day : DAYS_FULL) {
					date = StringUtils.removeStart(date, day+", ");
				}
				date = date.replaceFirst("(\\D)(\\d\\D)", "$1\\0$2");
				post.setDate(ZonedDateTime.of(
					LocalDate.parse(date, FORMAT_FULL),
					LocalTime.of(12, 0), PST
				));
			} catch(Exception e) {
				//unparsable
			}
		}
		else {
			try {
				post.setDate(ZonedDateTime.of(
					LocalDateTime.parse(date, FORMAT_SHORT),
					PST
				));
			} catch(Exception e) {
				//unparsable
			}
		}
			
		
	}
}
