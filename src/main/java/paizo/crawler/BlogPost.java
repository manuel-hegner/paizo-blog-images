package paizo.crawler;
import java.time.ZonedDateTime;
import java.util.List;

import org.jsoup.nodes.Element;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogPost {

	private String id;
	private String title;
	private String author;
	private ZonedDateTime date;
	private String[] tags;
	private List<BlogImage> images;
	private Element html;
}
