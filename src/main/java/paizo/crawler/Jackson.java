package paizo.crawler;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Jackson {

	public static final ObjectMapper MAPPER = create();
	public static final ObjectReader BLOG_READER = MAPPER.readerFor(BlogPost.class);
	public static final ObjectWriter BLOG_WRITER = MAPPER.writerFor(BlogPost.class);

	@SuppressWarnings("serial")
	private static ObjectMapper create() {
		YAMLFactory yaml = new YAMLFactory()
			.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
			.enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE);
		
		
		return new ObjectMapper(yaml)
			.addMixIn(Element.class, ElementMixIn.class)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.setSerializationInclusion(Include.NON_NULL)
			.findAndRegisterModules()
			.registerModule(new JavaTimeModule())
			.registerModule(new SimpleModule()
				.addDeserializer(Element.class, new FromStringDeserializer<>(Element.class) {
					@Override
					protected Element _deserialize(String html, DeserializationContext ctxt) throws IOException {
						return Jsoup.parseBodyFragment(html, "https://paizo.com").body().child(0);
					}
				}));
	}
	
	public static interface ElementMixIn {
		@Override
		@JsonValue
		String toString();
	}
}
