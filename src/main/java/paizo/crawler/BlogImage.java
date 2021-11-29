package paizo.crawler;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogImage {

	private String name;
	private String fullPath;
	private String artist;
	private String alt;
	@JsonIgnore
	private String wiki;
}
