package paizo.crawler;

public class Workflow {
	public static void main(String[] args) throws Exception {
		var result1 = BlogCollector.start(1);
		ArticleExtractor.run(result1);
		ArticleDetailsExtractor.main();
		DuplicateImageRemover.main();
		ImageDownloader.main();
		UnusedImageRemover.main();
		WikiHasher.main();
		BlogHasher.main();
		PageCreator.main();
	}
}
