package paizo.crawler.common.model;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.brachtendorf.jimagehash.hash.Hash;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class BlogImage {

	private String name;
	private String fullPath;
	private String artist;
	private String alt;
	private Hash hash;
	private long pixels;
	private WikiMappings wikiMappings = new WikiMappings();
	private String localFile;
	private Boolean usesTransparency;
	
	@JsonIgnore
	public File getLocalFileAsFile() {
		return new File(localFile);
	}
	
	@Getter
	@Setter
	public static class WikiMappings {
		private WikiImage pf;
		private WikiImage sf;
		
		@JsonIgnore
		public boolean hasMapping() {
			return pf != null || sf != null;
		}
	}
}
