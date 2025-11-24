package paizo.crawler.common.model;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogPost {

	private String id;
	private String url;
	private String title;
	private String author;
	private ZonedDateTime date;
	private String[] tags = new String[0];
	private List<BlogImage> images = new ArrayList<>();
	private Document html;
	private Boolean reported;
	@JsonIgnore
	private boolean changed = false;

	private final static DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
	public String printedDate() {
		if(date == null)
			return "unknown-date";
		else
			return DIR_FORMAT.format(date);
	}
	
	public boolean belongsToPf() {
		return Arrays.stream(tags).anyMatch(t->t.toLowerCase().contains("pathfinder"));
	}

	public boolean belongsToSf() {
		return Arrays.stream(tags).anyMatch(t->t.toLowerCase().contains("starfinder"));
	}

	public File postFile() {
		return new File("data/blog_posts/"+date.getYear()+"/"+id+".yaml");
	}
	
	public File detailsFile() {
		return new File("data/blog_posts_details/"+date.getYear()+"/"+id+".yaml");
	}

	public static List<File> allPostFiles() {
		var res = new ArrayList<File>();
		for(var d:new File("data/blog_posts").listFiles()) {
			for(var f:d.listFiles()) {
				res.add(f);
			}
		}
		return res;
	}
	
	public static List<File> allDetailsFiles() {
		var res = new ArrayList<File>();
		for(var d:new File("data/blog_posts_details").listFiles()) {
			for(var f:d.listFiles()) {
				res.add(f);
			}
		}
		return res;
	}
}
