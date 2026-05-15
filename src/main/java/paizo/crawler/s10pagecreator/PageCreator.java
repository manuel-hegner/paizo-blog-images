package paizo.crawler.s10pagecreator;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.net.URIBuilder;

import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fizzed.rocker.RenderingException;
import com.fizzed.rocker.runtime.RockerRuntime;

import paizo.crawler.common.Jackson;
import paizo.crawler.common.WikiText;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.ImageInfo;
import paizo.crawler.s11imagereporter.ImageReporter;

public class PageCreator {
	
	private static final Map<String, Instant> CHECKED_UP_TO = Map.of(
		"pf", LocalDate.of(2024, 9, 23).atStartOfDay().toInstant(ZoneOffset.UTC),
		"sf", LocalDate.of(2023, 04, 30).atStartOfDay().toInstant(ZoneOffset.UTC),
		"all", LocalDate.of(2023,04, 30).atStartOfDay().toInstant(ZoneOffset.UTC)
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
			.collect(Collectors.toMap(ImageInfo::getId, Function.identity()));
		
		var monthList = BlogPost.allDetailsFiles().stream()
			.map(f->{
				try {
					return Jackson.BLOG_READER.<BlogPost>readValue(f);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.sorted(Comparator.comparing(BlogPost::getDate).reversed().thenComparing(BlogPost::getId))
			.collect(Collectors.groupingBy(bp->printedDate(bp)))
			.entrySet()
			.stream()
			.sorted(Comparator.comparing(Entry::getKey))
			.toList();
		var allMonths = IntStream.range(0, monthList.size())
			.mapToObj(i->new Month(monthList.get(i).getKey(), i, monthList.get(i).getValue()))
			.toList();
		
		var pool = Executors.newVirtualThreadPerTaskExecutor();
		var futures = List.of(
			pool.submit(()->createApiJsons(pages, allMonths, images)),
			pool.submit(()->createHTMLPages(pages, allMonths, images, "pf")),
			pool.submit(()->createHTMLPages(pages, allMonths, images, "sf")),
			pool.submit(()->createHTMLPages(pages, allMonths, images, "all"))
		);
		futures.forEach(f->{
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		});
		pool.shutdown();
		
		File last = new File(pages, allMonths.get(allMonths.size()-1).month()+"-all.html");
		FileUtils.copyFile(last, new File(pages, "index.html"));
	}
	
	private final static DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
	private static String printedDate(BlogPost bp) {
		if(bp.getDatePacific() == null)
			return "unknown-date";
		else
			return DIR_FORMAT.format(bp.getDatePacific());
	}

	private static Void createHTMLPages(File pages, List<Month> allMonths, Map<String, ImageInfo> images, String mode) throws RenderingException, IOException {
		System.out.println("Generating HTML pages in mode "+mode);
		var months = filterMonths(mode, allMonths);
		
		var monthCounts = months.stream()
				.map(e->new MonthCount(
					e.month(),
					e.posts().stream()
						.filter(po->po.getDate() == null || po.getDate().isAfter(CHECKED_UP_TO.get(mode)))
						.filter(po->po.getImages()!=null)
						.flatMap(po->po.getImages().stream())
						.filter(i->!images.get(i.getId()).getWikiMappings().hasMapping())
						.count()
				))
				.sorted(Comparator.comparing(MonthCount::month))
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
		return null;
	}

	private static record APIJson(String id, String name, String text, String url) {}
	private static Void createApiJsons(File pages, List<Month> allMonths, Map<String, ImageInfo> images) {
		File apiDir = new File(pages, "api");
		apiDir.mkdir();
		System.out.println("Generating API JSONs");
		
		allMonths.stream()
			.flatMap(m->m.posts().stream())
			.forEach(post-> {
				post.getImages().forEach(bImg -> {
					try {
						var ii = images.get(bImg.getId());
						if(ii == null || ii.getOptimizedFile() == null)
							return;
						Jackson.JSON.writerWithDefaultPrettyPrinter().writeValue(
							new File(apiDir, ii.getId()+".json"),
							new APIJson(
								ii.getId(),
								ImageReporter.imageName(ii),
								WikiText.wikitext(post, bImg),
								"https://raw.githubusercontent.com/manuel-hegner/paizo-blog-images/main/"+ii.getOptimizedFile().toString().replace('\\','/')
							)
						);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			});
		return null;
	}

	private static List<Month> filterMonths(String mode, List<Month> allMonths) {
		if("all".equals(mode)) return allMonths;
		if(!"pf".equals(mode) && !"sf".equals(mode)) throw new IllegalStateException();
		
		var res = new ArrayList<Month>();
		int i=0;
		for(var m:allMonths) {
			res.add(new Month(
				m.month(),
				i++,
				m.posts()
					.stream()
					.flatMap(p->filterPost(mode, p))
					.filter(Objects::nonNull)
					.toList()
			));
		}
		return res;
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

	private static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy").withLocale(Locale.US);
	public static String buildCitationLink(String wiki, BlogPost post) {
		var url = new URIBuilder()
				.setScheme("https")
				.setHost(wiki)
				.setPath("wiki/Special:FormEdit/Web_citation/Facts:Paizo_blog/"+post.getId())
				.addParameter("Facts/Web citation[Name]", post.getTitle())
				.addParameter("Facts/Web citation[Author]", Optional.ofNullable(post.getAuthor()).orElse(""))
				.addParameter("Facts/Web citation[Release date]", post.getDate()==null?"":DATE_FORMAT.format(post.getDatePacific()))
				.addParameter("Facts/Web citation[Website name]", "Paizo blog")
				.addParameter("Facts/Web citation[Website]", "https://paizo.com/community/blog/"+post.getId());
		try {
			return url.build().toString();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}
}
