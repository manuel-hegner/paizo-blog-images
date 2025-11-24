package paizo.crawler.s11imagereporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.utils.FileUpload;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.WikiText;
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.ImageInfo;

@Slf4j
public class ImageReporter {
	
	private final static long CHANNEL_BLOG_WATCH = 1287958801584099380L;

	public static void main(String... args) throws IOException, InterruptedException {
		var images = Files.walk(Path.of("data/images/"))
			.map(Path::toFile)
			.filter(f->"info.yaml".equals(f.getName()))
			.map(f->{
				try {
					return Jackson.MAPPER.readValue(f, ImageInfo.class);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.collect(Collectors.toMap(ImageInfo::getId, Function.identity()));
		
		var jda = JDABuilder.createDefault(args[0])
				.build()
				.awaitReady();
		
		BlogPost.allDetailsFiles().stream()
			.map(f->{
				try {
					return Jackson.BLOG_READER.<BlogPost>readValue(f);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.filter(p->!Boolean.TRUE.equals(p.getReported()))
			.sorted(Comparator.comparing(BlogPost::getDate))
			.forEach(p->report(jda, p, images));
		
		jda.shutdown();
	}
	
	private static record Image(BlogImage blogImage, ImageInfo imageInfo, String status) {
		public Image(BlogImage blogImage, ImageInfo imageInfo) {
			this(blogImage, imageInfo, extractCase(blogImage, imageInfo));
		}
		
		private static String extractCase(BlogImage blogImage, ImageInfo info) {
			var mappings = info.getWikiMappings();
			if(mappings == null || !mappings.hasMapping()) {
				return "Might not have been uploaded.";
			}
			String status = "";
			if(mappings.getPf()!=null){
				String sub = "";
				if(mappings.getPf().getPixels()<info.getPixels()) {
					sub+="* the wiki version has a lower resolution\n";
				}
				if(!mappings.getPf().getUsesTransparency() && info.getUsesTransparency()) {
					sub+="* the wiki version is not transparent\n";
				}
				if(!sub.isBlank()) {
					status+="Already uploaded to Pathfinderwiki as ["
							+mappings.getPf().getName()
							+"](https://pathfinderwiki.com/wiki/File:"
							+mappings.getPf().getName()
							+"):\n"
							+sub;
				}
			}
			if(mappings.getSf()!=null){
				String sub = "";
				if(mappings.getSf().getPixels()<info.getPixels()) {
					sub+="* the wiki version has a lower resolution\n";
				}
				if(!mappings.getSf().getUsesTransparency() && info.getUsesTransparency()) {
					sub+="* the wiki version is not transparent\n";
				}
				if(!sub.isBlank()) {
					status+="Already uploaded to Pathfinderwiki as ["
							+mappings.getSf().getName()
							+"](https://starfinderwiki.com/wiki/File:"
							+mappings.getSf().getName()
							+"):\n"
							+sub;
				}
			}
			return StringUtils.trimToNull(status);
		}
	}

	private static void report(JDA jda, BlogPost post, Map<String, ImageInfo> images) {
		try {
			var toReport = post.getImages().stream()
				.map(i->new Image(i,images.get(i.getId())))
				.filter(i->i.status() != null)
				.filter(i->i.imageInfo.getOptimizedFile() != null)
				.toList();
			
			if(!toReport.isEmpty()) {
				log.info("Reporting on {} images from {}", toReport.size(), post.getTitle());
				
				var sb = new StringBuilder();
				
				sb
					.append("**"+post.getDate().toLocalDate()+" post: ")
					.append("["+post.getTitle()+"]")
					.append("("+post.getUrl()+")")
					.append("**\n")
					
					.append("This article contains "+toReport.size()+" interesting image(s). ")
					.append("I am sometimes silly about this and could be wrong. Please check my suggestions. ")
					.append("For example we could already have a nicer version of the same image. ")
					.append("The given Wikitext is a best effor basis, but could also contain mistakes.\n")
					.append("If you check/upload them, please react with a checkmark to inform your peers. ");
				
				var channel = jda.getTextChannelById(CHANNEL_BLOG_WATCH);
				channel
					.sendMessage(sb.toString())
					.queue();
				
				int i=1;
				for(var p:toReport) {
					try {
						channel
							.sendMessage("**"+(i++)+". "+imageName(p.imageInfo)+"**\n"
								+"Status: "+p.status()+"\n"
								+"[Upload to pathfinderwiki]("+buildUrl("pathfinderwiki.com", post, p.blogImage, p.imageInfo)+") or [Upload to starfinderwiki]("+buildUrl("starfinderwiki.com", post, p.blogImage, p.imageInfo)+")")
							.addFiles(FileUpload.fromData(p.imageInfo().getOptimizedFile()).setName(imageName(p.imageInfo)))
							.setSuppressEmbeds(true)
							.queue();
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			post.setReported(Boolean.TRUE);
			File target = post.detailsFile();
			Jackson.BLOG_WRITER.writeValue(target, post);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String imageName(ImageInfo img) {
		return FilenameUtils.removeExtension(img.getName())+"."+img.getOptimizedExtension();
	}

	public static String buildUrl(String wiki, BlogPost post, BlogImage bImg, ImageInfo iImg) {
		var url = new URIBuilder()
				.setScheme("https")
				.setHost(wiki)
				.setPath("wiki/Widget:UploadHelper")
				.addParameter("name", imageName(iImg));
				addCompressed(url, "text", WikiText.wikitext(post, bImg));
				addCompressed(url, "url", "https://raw.githubusercontent.com/manuel-hegner/paizo-blog-images/main/"+iImg.getOptimizedFile().toString().replace('\\','/'));
		try {
			return url.build().toString();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void addCompressed(URIBuilder url, String key, String val) {
		var compr = compress(val);
		if(compr.length()<val.length()) {
			url.addParameter(key+"Gz", compr);
		}
		else {
			url.addParameter(key, val);
		}
	}

	private static String compress(String txt) {
		try (var baos = new ByteArrayOutputStream();
			var out = new OutputStreamWriter(new DeflaterOutputStream(baos, new Deflater(9)))) {
			out.write(txt);
			out.close();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
		} catch(Exception e) {
			e.printStackTrace();
			return "";
		}
	}

}
