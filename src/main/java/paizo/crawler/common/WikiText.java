package paizo.crawler.common;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;

public class WikiText {
	private final static DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
	public static String wikitext(BlogPost post, BlogImage img) {
		return "== Summary ==\n"
				+ "\n"
				+ "{{File\n"
				+ "| year     = "+(post.getDate()==null?"":post.getDate().getYear())+"\n"
				+ "| copy     = Paizo Inc.\n"
				+ "| artist   = "+Objects.requireNonNullElse(img.getArtist(),"")+"\n"
				+ "| print    = \n"
				+ "| page     = \n"
				+ "| web      = {{Cite web\n"
				+ "  | author = "+Optional.ofNullable(post.getAuthor()).map(a->"[["+a+"]]").orElse("")+"\n"
				+ "  | date   = "+(post.getDate()==null?"":FORMAT.format(post.getDate()))+"\n"
				+ "  | title  = "+post.getTitle()+"\n"
				+ "  | page   = Paizo Blog\n"
				+ "  | url    = https://paizo.com/community/blog/"+post.getId()+"\n"
				+ "  }}   \n"
				+ "| summary  = "+img.getAlt()+"\n"
				+ "| keyword1 = \n"
				+ "| keyword2 = \n"
				+ "}}   \n"
				+ "\n"
				+ "== Licensing ==\n"
				+ "\n"
				+ "{{Paizo CUP|blog|url=https://paizo.com/community/blog/"+post.getId()+"}}";
	}
}
