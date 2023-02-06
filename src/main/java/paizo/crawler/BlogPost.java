package paizo.crawler;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	private final static DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
	public String printedDate() {
		if(date == null)
			return "unknown-date";
		else
			return DIR_FORMAT.format(date);
	}

	@JsonIgnore
	public boolean checked() {
		return date!=null && date.toLocalDate().isBefore(LocalDate.of(2019,11,1));
	}
}
