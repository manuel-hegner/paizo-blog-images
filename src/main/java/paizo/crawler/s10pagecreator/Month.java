package paizo.crawler.s10pagecreator;

import java.util.List;

import paizo.crawler.common.model.BlogPost;

public record Month(String month, List<BlogPost> posts) {

}
