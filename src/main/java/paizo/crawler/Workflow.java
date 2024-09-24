package paizo.crawler;

import paizo.crawler.s01blogcollector.BlogCollector;
import paizo.crawler.s02articleextractor.ArticleExtractor;
import paizo.crawler.s03articledetailsextractor.ArticleDetailsExtractor;
import paizo.crawler.s04duplicateimageremover.DuplicateImageRemover;
import paizo.crawler.s05imagedownloader.ImageDownloader;
import paizo.crawler.s06imageoptimizer.ImageOptimizer;
import paizo.crawler.s07unusedimageremover.UnusedImageRemover;
import paizo.crawler.s08wikihasher.WikiHasher;
import paizo.crawler.s09bloghasher.BlogHasher;
import paizo.crawler.s10pagecreator.PageCreator;
import paizo.crawler.s11imagereporter.ImageReporter;

public class Workflow {
	public static void main(String[] args) throws Exception {
		var result1 = BlogCollector.start(1);
		ArticleExtractor.run(result1);
		ArticleDetailsExtractor.main();
		DuplicateImageRemover.main();
		ImageDownloader.main();
		ImageOptimizer.main();
		UnusedImageRemover.main();
		WikiHasher.main();
		BlogHasher.main();
		PageCreator.main();
		ImageReporter.main(args[0]);
	}
}
