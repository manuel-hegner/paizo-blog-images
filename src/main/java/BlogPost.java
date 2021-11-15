import java.time.ZonedDateTime;

import org.jsoup.nodes.Element;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BlogPost {

	private String id;
	private String title;
	private ZonedDateTime date;
	private Element html;
}
