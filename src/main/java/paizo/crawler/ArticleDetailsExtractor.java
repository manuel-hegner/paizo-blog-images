package paizo.crawler;
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
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArticleDetailsExtractor implements PBICallable {
	
	public static void main(String... args) throws InterruptedException {
		var pool = new MyPool("Article Details Extractor");
		for(var f:new File("blog_posts").listFiles()) {
			pool.submit(new ArticleDetailsExtractor(f));
		}
		pool.shutdown();
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
		moveTimeZone(post);
		post.setTitle(findTitle(post));
		post.setTags(findTags(post));
		post.setImages(findImages(post));
		post.setAuthor(findAuthor(post));
		
		post.setHtml(null);
		
		File target = new File(new File("blog_posts_details"), file.getName());
		target.getParentFile().mkdirs();
		Jackson.BLOG_WRITER.writeValue(target, post);
	}
	
	private static final ZoneId ZONE = ZoneId.of("US/Pacific");
	private void moveTimeZone(BlogPost post) {
		if(post.getDate()==null)
			return;
		post.setDate(post.getDate().withZoneSameInstant(ZONE));
	}

	private String findAuthor(BlogPost post) {
        var authEl = post.getHtml().getElementsByAttributeValueContaining("style", "margin-left: 20px; font-weight: bold;").first();
        if(authEl == null)
        	return null;
        return authEl.ownText();
	}

	private List<BlogImage> findImages(BlogPost post) {
		var imgs = post.getHtml()
			.getElementsByAttributeValueStarting("src", "https://cdn.paizo.com/")
			.stream()
			.map(this::toImage)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		if(imgs.isEmpty())
			return null;
		return imgs;
	}
	
	private BlogImage toImage(Element e) {
		BlogImage img = new BlogImage();
		String src = e.attr("src");
		String alt = e.attr("alt");
		if(StringUtils.isBlank(src))
			return null;
		if(src.contains("?"))
			src=src.substring(0,src.indexOf("?"));
		
		var sib = e.parent().nextElementSibling();
		if(sib != null) {
			String nextText = sib.text();
			if(nextText.contains(" by ")) {
				String by = nextText.substring(nextText.indexOf(" by ")+4);
				by = StringUtils.removeEnd(by, ".");
				if(by.contains(" from "))
					by = by.substring(0, by.indexOf(" from "));
				img.setArtist(by);
			}
		}
		
		
		img.setFullPath(src);
		img.setName(src.substring(src.lastIndexOf("/")+1));
		img.setAlt(StringUtils.stripToNull(alt.replaceAll("\s+", " ")));
		return img;
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
		if(post.getDate()!=null)
			return;

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
