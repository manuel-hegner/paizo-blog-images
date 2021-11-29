package paizo.crawler;

import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class HTMLCleaner {

	private static final Pattern COMMENTS_LINK = Pattern.compile("^https://paizo.com/community/blog/.*\\#discuss$");
	
	public static void clean(Document doc) {
		doc.getElementsByTag("script").remove();
		doc.getElementsByTag("nav").remove();
		doc.getElementsByClass("sub-menu").remove();
		doc.getElementsByAttributeValueMatching("href", COMMENTS_LINK).forEach(Element::empty);
		doc.getElementsByTag("time").forEach(e-> {
			if(e.hasAttr("datetime"))
				e.empty();
		});
	}
}
