package paizo.crawler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HashedImage {

	private String name;
	private String url;
	private String hash;
}
