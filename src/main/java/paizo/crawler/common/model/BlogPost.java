package paizo.crawler.common.model;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
	private String[] tags = new String[0];
	private List<BlogImage> images = new ArrayList<>();
	private Element html;

	private final static DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
	public String printedDate() {
		if(date == null)
			return "unknown-date";
		else
			return DIR_FORMAT.format(date);
	}
	
	public File detailsFile() {
		return new File("data/blog_posts_details", id+".yaml");
	}

	public boolean belongsToPf() {
		return Arrays.stream(tags).anyMatch(t->t.toLowerCase().contains("pathfinder"));
	}

	public boolean belongsToSf() {
		return Arrays.stream(tags).anyMatch(t->t.toLowerCase().contains("starfinder"));
	}
}
