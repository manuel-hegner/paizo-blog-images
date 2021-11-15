import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class Jackson {

	public static final ObjectMapper MAPPER = create();

	private static ObjectMapper create() {
		YAMLFactory yaml = new YAMLFactory()
			.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
			.enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE);
		
		
		return new ObjectMapper(yaml)
			.addMixIn(Element.class, ElementMixIn.class);
	}
	
	public static interface ElementMixIn {
		@Override
		@JsonValue
		String toString();
		
		@JsonCreator
		public static Element parse(String html) {
			return Jsoup.parse(html);
		}
	}
}
