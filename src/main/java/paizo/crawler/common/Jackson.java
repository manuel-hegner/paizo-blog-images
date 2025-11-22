package paizo.crawler.common;
import java.io.IOException;
import java.math.BigInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import dev.brachtendorf.jimagehash.hash.Hash;
import paizo.crawler.common.model.BlogPost;

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
			.addMixIn(Hash.class, HashMixIn.class)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.setSerializationInclusion(Include.NON_NULL)
			.findAndRegisterModules()
			.registerModule(new ParameterNamesModule())
			.registerModule(new JavaTimeModule())
			.registerModule(new SimpleModule()
				.addDeserializer(Document.class, new FromStringDeserializer<>(Document.class) {
					protected Document _deserialize(String html, DeserializationContext ctxt) throws IOException {
						return Jsoup.parse(html, "https://paizo.com");
					}
				})
				/*.addDeserializer(Hash.class, new FromStringDeserializer<>(Hash.class) {
					protected Hash _deserialize(String raw, DeserializationContext ctxt) throws IOException {
						try(var in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(raw)))) {
							try {
								return (Hash) in.readObject();
							} catch (ClassNotFoundException | IOException e) {
								throw new RuntimeException(e);
							}
						}
					}
				})
				.addSerializer(Hash.class, new StdScalarSerializer<Hash>(Hash.class) {
					public void serialize(Hash hash, JsonGenerator gen, SerializerProvider provider) throws IOException {
						var baos = new ByteArrayOutputStream();
						try(var out = new DataOutputStream(baos)) {
							hash.getBig
						}
						gen.writeString(Base64.getEncoder().encodeToString(baos.toByteArray()));
					}
				})*/);
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
