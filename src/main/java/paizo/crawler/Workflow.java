package paizo.crawler;

public class Workflow {
	public static void main(String[] args) throws Exception {
		BlogCollector.start(1);
		ArticleExtractor.main();
		ArticleDetailsExtractor.main();
		ImageDownloader.main();
	}
}
