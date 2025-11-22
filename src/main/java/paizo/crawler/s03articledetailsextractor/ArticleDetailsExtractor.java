package paizo.crawler.s03articledetailsextractor;
import java.io.File;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
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
		for(var d:new File("data/blog_posts").listFiles()) {
			for(var f:d.listFiles()) {
				pool.submit(new ArticleDetailsExtractor(blacklist, f));
			}
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
		moveTimeZone(post);
		post.setTitle(findTitle(post));
		post.setTags(findTags(post));
		post.setImages(findImages(post));
		post.setAuthor(findAuthors(post));

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

	private String findAuthors(BlogPost post) {
		var byline = post.getHtml().getElementsByClass("byline").getFirst();
		return StringUtils.trimToNull(byline.getElementsByClass("name").stream()
				.map(e->e.text())
				.map(a->StringUtils.removeEnd(a.trim(), ",").trim())
				.map(a->a.contains("(")?a.substring(0, a.indexOf('(')).trim():a)
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.joining(";")));
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

		var caption = e.parent().getElementsByTag("figcaption").first();
		if(caption != null) {
			String captionTxt = caption.text();
			if(captionTxt.contains(" by ")) {
				String by = captionTxt.substring(captionTxt.indexOf(" by ")+4);
				by = StringUtils.removeEnd(by, ".");
				if(by.contains(" from "))
					by = by.substring(0, by.indexOf(" from "));
				img.setArtist(by);
			}
			else {
				img.setArtist(captionTxt);
			}
		}


		img.setCandidatePaths(srcs);
		img.setAlt(StringUtils.stripToNull(alt.replaceAll("\s+", " ")));
		return img;
	}
	
	private static final String[] IMAGE_TYPES = {
		"gif", "jpeg", "jpg", "png", "webp", "avif"
	};

	private String[] findTags(BlogPost post) {
		String[] tags = post.getHtml()
				.getElementsByClass("tags").stream()
				.flatMap(e->e.getElementsByTag("a").stream())
				.map(e->e.text().trim())
				.filter(StringUtils::isNotBlank)
				.sorted()
				.toArray(String[]::new);
		if(tags.length == 0)
			return null;
		return tags;
	}

	private String findTitle(BlogPost post) {
		var first = post.getHtml()
			.getElementsByClass("title-and-author")
			.first()
			.getElementsByTag("h1")
			.first();
		if (first != null)
			return first.text();
		first = post.getHtml().getElementsByTag("h1").first();
		if (first != null)
			return first.text();
		return null;
	}
}
