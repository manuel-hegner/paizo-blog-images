package paizo.crawler.common.model;

import dev.brachtendorf.jimagehash.hash.Hash;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WikiImage {

	private String name;
	private String url;
	private String wiki;
	private Boolean usesTransparency;
	private Hash hash;
	private long pixels = Long.MAX_VALUE;
}
