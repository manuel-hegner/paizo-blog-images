package paizo.crawler;
import dev.brachtendorf.jimagehash.hash.Hash;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogImage {

	private String name;
	private String fullPath;
	private String artist;
	private String alt;
	private Hash hash;
	private String wikiImage;
}
