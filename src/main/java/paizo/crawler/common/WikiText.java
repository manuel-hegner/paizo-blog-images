package paizo.crawler.common;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;

public class WikiText {
	public final static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-dd-MM", Locale.US);
	public static String wikitext(BlogPost post, BlogImage img) {
		return "== Summary ==\n"
				+ "\n"
				+ "{{File\n"
				+ "| year     = "+(post.getDate()==null?"":post.getDate().getYear())+"\n"
				+ "| copy     = Paizo Inc.\n"
				+ "| artist   = "+Objects.requireNonNullElse(img.getArtist(),"")+"\n"
				+ "| print    = \n"
				+ "| page     = \n"
				+ "| web      = {{Cite|Paizo blog/"+post.getId()+"}}\n"
				+ "| summary  = "+Optional.ofNullable(img.getAlt()).orElse("")+"\n"
				+ "| keyword1 = \n"
				+ "| keyword2 = \n"
				+ "}}   \n"
				+ "\n"
				+ "== Licensing ==\n"
				+ "\n"
				+ "{{Paizo CUP|blog|source=Facts:Paizo blog/"+post.getId()+"}}";
	}

	public static String blogWikitext(BlogPost post, BlogImage img) {
		return "{{Facts/Web citation\n"
				+ "  | Author = "+Optional.ofNullable(post.getAuthor()).orElse("")+"\n"
				+ "  | Release date = "+(post.getDate()==null?"":DATE_FORMAT.format(post.getDate()))+"\n"
				+ "  | Name = "+post.getTitle()+"\n"
				+ "  | Website name=Paizo blog\n"
				+ "  | Website = "+post.getUrl()+"\n"
				+ "}}";
	}
}
