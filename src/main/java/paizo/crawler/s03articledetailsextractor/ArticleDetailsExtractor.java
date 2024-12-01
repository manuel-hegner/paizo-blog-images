package paizo.crawler.s03articledetailsextractor;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;

@RequiredArgsConstructor
public class ArticleDetailsExtractor implements PBICallable {

	public static void main(String... args) throws Exception {
		var blacklist = Jackson.MAPPER.readValue(new File("data/blacklist.yaml"), Pattern[].class);
		var pool = new MyPool("Article Details Extractor");
		for(var f:new File("data/blog_posts").listFiles()) {
			pool.submit(new ArticleDetailsExtractor(blacklist, f));
		}
		pool.shutdown();
	}

	private final Pattern[] blacklist;
	private final File file;

	@Override
	public String getBlogId() {
		return file.getName();
	}

	@Override
	public void run() throws Exception {
		BlogPost post = Jackson.BLOG_READER.readValue(file);

		removeInvisible(post);
		findDate(post);
		moveTimeZone(post);
		post.setTitle(findTitle(post));
		post.setTags(findTags(post));
		post.setImages(findImages(post));
		post.setAuthor(findAuthor(post));

		post.setHtml(null);

		File target = post.detailsFile();
		if(!target.exists()) {
			target.getParentFile().mkdirs();
			Jackson.BLOG_WRITER.writeValue(target, post);
		}
	}

	private void removeInvisible(BlogPost post) {
        post.getHtml()
            .getElementsByAttributeValueMatching("style", "display *: *none")
            .remove();
    }

    private static final ZoneId ZONE = ZoneId.of("US/Pacific");
	public static void moveTimeZone(BlogPost post) {
		if(post.getDate()==null)
			return;
		post.setDate(post.getDate().withZoneSameInstant(ZONE));
	}

	private String findAuthor(BlogPost post) {
        var authEl = post.getHtml().getElementsByAttributeValueContaining("style", "margin-left: 20px; font-weight: bold;").first();
        if(authEl != null)
        	return StringUtils.trimToNull(authEl.ownText());

        var article = post.getHtml().getElementsByAttributeValue("itemprop", "articlebody").first();
        if(article == null)
        	return null;
        var last = article.children().last();
        if(last.is("p") && last.ownText().length()<40)
        	return StringUtils.trimToNull(last.ownText());

        return null;
	}

	private List<BlogImage> findImages(BlogPost post) {
		var imgs = post.getHtml()
			.getElementsByAttribute("src")
			.stream()
			.filter(e->!e.attr("src").contains("image/button"))
			.filter(e->!e.attr("src").contains("youtube.com"))
			.map(this::toImage)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		if(imgs.isEmpty())
			return null;
		return imgs;
	}

	private BlogImage toImage(Element e) {
		BlogImage img = new BlogImage();
		List<String> srcs = Lists.newArrayList(e.absUrl("src"));
		if(e.parent().tagName().equals("a")) {
			srcs.add(e.parent().absUrl("href"));
		}
		String alt = e.attr("alt");
		
		srcs = srcs.stream()
			.map(src->src.contains("?")?src.substring(0,src.indexOf("?")):src)
			.map(src->src.trim().replaceAll("\\s", ""))
			.filter(StringUtils::isNotBlank)
			.filter(src->{
				for(var ext:IMAGE_TYPES) {
					if(src.toLowerCase().endsWith("."+ext))
						return true;
				}
				return false;
			})
			.filter(src->!Arrays.stream(blacklist).anyMatch(p->p.matcher(src).matches()))
			.distinct()
			.toList();
		
		if(srcs.size()==0) return null;

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


		img.setCandidatePaths(srcs);
		img.setAlt(StringUtils.stripToNull(alt.replaceAll("\s+", " ")));
		return img;
	}
	
	private static final String[] IMAGE_TYPES = {
		"gif", "jpeg", "jpg", "png", "webp"
	};

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
