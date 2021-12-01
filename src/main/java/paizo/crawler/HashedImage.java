package paizo.crawler;

import dev.brachtendorf.jimagehash.hash.Hash;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HashedImage {

	private String name;
	private String url;
	private Hash hash;
}
