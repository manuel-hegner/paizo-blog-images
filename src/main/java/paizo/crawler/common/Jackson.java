package paizo.crawler.common;

import java.math.BigInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import dev.brachtendorf.jimagehash.hash.Hash;
import paizo.crawler.common.model.BlogPost;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.deser.std.FromStringDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

public class Jackson {

	public static final YAMLMapper MAPPER = create(YAMLMapper.builder()
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE));
	public static final JsonMapper JSON = create(JsonMapper.builder());
	public static final ObjectReader BLOG_READER = MAPPER.readerFor(BlogPost.class);
	public static final ObjectWriter BLOG_WRITER = MAPPER.writerFor(BlogPost.class);

	private static <R extends ObjectMapper, T extends MapperBuilder<R,?>> R create(T mapper) {
		return mapper
			.addMixIn(Element.class, ElementMixIn.class)
			.addMixIn(Hash.class, HashMixIn.class)
			.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(Include.NON_NULL))
			.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(Include.NON_NULL))
			.addModule(new SimpleModule()
				.addDeserializer(Document.class, new FromStringDeserializer<>(Document.class) {
					protected Document _deserialize(String html, DeserializationContext ctxt) {
						return Jsoup.parse(html, "https://paizo.com");
					}
				})
			)
			.build();
	}
	
	public static interface ElementMixIn {
		@Override
		@JsonValue
		String toString();
	}
	
	public static abstract class HashMixIn extends Hash {
		@JsonCreator
		public HashMixIn(
				@JsonProperty("hashValue")BigInteger hashValue,
				@JsonProperty("bitResolution")int bitResolution,
				@JsonProperty("algorithmId")int algorithmId) {
			super(hashValue, bitResolution, algorithmId);
		}
	}
}
