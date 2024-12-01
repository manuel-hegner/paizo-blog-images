package paizo.crawler.s11imagereporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

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
		
		Arrays.stream(new File("data/blog_posts_details").listFiles())
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
					.append("(https://paizo.com/community/blog/"+post.getId()+")")
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
					var name = FilenameUtils.removeExtension(p.imageInfo().getName())+".webp";
					channel
						.sendMessage((i++)+". ["+name+"]"
							+"(https://raw.githubusercontent.com/manuel-hegner/paizo-blog-images/main/"+p.imageInfo().getOptimizedFile().toString().replace('\\','/')+"):\n"
							+"Status: "+p.status()+"\n"
							+"```\n"+WikiText.wikitext(post, p.blogImage())+"\n```")
						.addFiles(FileUpload.fromData(p.imageInfo().getOptimizedFile()).setName(name))
						.queue();
				}
			}
			
			post.setReported(Boolean.TRUE);
			File target = post.detailsFile();
			Jackson.BLOG_WRITER.writeValue(target, post);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
