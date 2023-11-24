package paizo.crawler.s10pagecreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fizzed.rocker.runtime.RockerRuntime;

import paizo.crawler.common.Jackson;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.ImageInfo;

public class PageCreator {
	
	private static final Map<String, LocalDate> CHECKED_UP_TO = Map.of(
		"pf", LocalDate.of(2021,12,1),
		"sf", LocalDate.of(1000,1,1),
		"all", LocalDate.of(1000,1,1)
	);

	public static void main(String... args) throws IOException {
		RockerRuntime.getInstance().setReloading(true);
		File pages = new File("docs/");
		pages.mkdir();
		
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
			.collect(Collectors.toMap(ImageInfo::getFullPath, Function.identity()));
		
	
		var allMonths = Arrays.stream(new File("data/blog_posts_details").listFiles())
			.map(f->{
				try {
					return Jackson.BLOG_READER.<BlogPost>readValue(f);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.sorted(Comparator.comparing(BlogPost::getDate).reversed())
			.collect(Collectors.groupingBy(BlogPost::printedDate))
			.entrySet()
			.stream()
			.map(e->new Month(e.getKey(), e.getValue()))
			.sorted(Comparator.comparing(Month::month))
			.toList();
		
		for(String mode:new String[] {"pf","sf","all"}) {
			var months = filterMonths(mode, allMonths);
			
			var monthCounts = months.stream()
					.map(e->new MonthCount(
						e.month(),
						e.posts().stream()
							.filter(po->po.getDate() == null || po.getDate().toLocalDate().isAfter(CHECKED_UP_TO.get(mode)))
							.filter(po->po.getImages()!=null)
							.flatMap(po->po.getImages().stream())
							.filter(i->!images.get(i.getFullPath()).getWikiMappings().hasMapping())
							.count()
					))
					.sorted(Comparator.comparing(MonthCount::month).reversed())
					.collect(Collectors.toList());
				
				for(var month : months) {
					Page p = Page.template(
						mode,
						month,
						monthCounts,
						images,
						CHECKED_UP_TO.get(mode)
					);
					
					Files.writeString(
						new File(pages, month.month()+"-"+mode+".html").toPath(),
						p.render().toString()
					);
				}
		}
		
		
		File last = new File(pages, allMonths.get(allMonths.size()-1).month()+"-all.html");
		FileUtils.copyFile(last, new File(pages, "index.html"));
	}

	private static List<Month> filterMonths(String mode, List<Month> allMonths) {
		if("all".equals(mode)) return allMonths;
		if(!"pf".equals(mode) && !"sf".equals(mode)) throw new IllegalStateException();
		
		return allMonths.stream()
			.map(m->new Month(
				m.month(),
				m.posts()
					.stream()
					.flatMap(p->filterPost(mode, p))
					.filter(Objects::nonNull)
					.toList()
			))
			.toList();
	}

	private static Stream<BlogPost> filterPost(String mode, BlogPost p) {
		try {
			TokenBuffer tb = new TokenBuffer(Jackson.MAPPER.getFactory().getCodec(), false);
			Jackson.MAPPER.writeValue(tb, p);
			var copy = Jackson.MAPPER.readValue(tb.asParser(), BlogPost.class);
			
			if("sf".equals(mode)) {
				if(copy.belongsToPf() && ! copy.belongsToSf()) return Stream.of();
			}
			if("pf".equals(mode)) {
				if(copy.belongsToSf() && ! copy.belongsToPf()) return Stream.of();
			}
			
			return Stream.of(copy);
		} catch(Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
