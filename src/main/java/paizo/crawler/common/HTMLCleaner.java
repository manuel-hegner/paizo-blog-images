package paizo.crawler.common;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;

public class HTMLCleaner {

	
	public static void clean(Document doc) {
		doc.forEachNode(n->{if(n instanceof Comment) n.remove();});
		doc.getElementsByTag("style").remove();
		doc.getElementsByAttributeValue("rel", "preload");
		doc.getElementsByTag("script").remove();
		doc.getElementsByTag("nav").remove();
		doc.getElementsByTag("footer").remove();
		doc.getElementsByTag("header").remove();
		doc.getElementsByClass("header-wrapper").remove();
		doc.getElementsByClass("blog_feed_row").remove();
		doc.getElementsByTag("time").forEach(e-> {
			if(e.hasAttr("datetime"))
				e.empty();
		});
	}
}
