package paizo.crawler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fizzed.rocker.runtime.RockerRuntime;

public class PageCreator {

	public static void main(String... args) throws IOException {
		RockerRuntime.getInstance().setReloading(true);
		File pages = new File("docs/");
		pages.mkdir();
		
	
		var allPosts = Arrays.stream(new File("blog_posts_details").listFiles())
			.map(f->{
				try {
					return Jackson.BLOG_READER.<BlogPost>readValue(f);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.sorted(Comparator.comparing(BlogPost::getDate).reversed())
			.collect(Collectors.groupingBy(BlogPost::printedDate));
		
		var posts = allPosts.entrySet()
			.stream()
			.sorted(Comparator.comparing(Entry::getKey))
			.collect(Collectors.toList());
		
		for(var month : posts) {
			
			Page p = Page.template(
					month.getKey(),
					month.getValue(),
					allPosts.keySet().stream()
						.sorted()
						.collect(Collectors.toList())
			);
			
			Files.writeString(
				new File(pages, month.getKey()+".html").toPath(),
				p.render().toString()
			);
		}
		File last = new File(pages, posts.get(posts.size()-1).getKey()+".html");
		FileUtils.copyFile(last, new File(pages, "index.html"));
	}
}
