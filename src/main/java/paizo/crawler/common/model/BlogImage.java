package paizo.crawler.common.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogImage {

	private String id;
	private List<String> candidatePaths;
	private String artist;
	private String alt;
}
