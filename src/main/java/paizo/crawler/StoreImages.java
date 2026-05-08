package paizo.crawler;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;

import com.google.common.primitives.Bytes;

import lombok.extern.slf4j.Slf4j;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.s05imagedownloader.ImageDownloader;
import paizo.crawler.s06imageoptimizer.ImageOptimizer;

@Slf4j
public class StoreImages {
	
	private static record WikInfo(TreeMap<String, Product> values) {}
	private static record Product(
		Instant lastChanged,
		Instant created,
		Props properties
	) {}
	private static record Props(
		String sku,
		String upc,
		String name,
		String price,
		String url,
		String storeImage,
		Ratings ratings
	) {}
	private static record Ratings(
		double totalReviews,
		double averageScore
	) {}
	
	public static void main(String[] args) throws Exception {
		var wikiInfo = Jackson.JSON.readValue(
			URI.create("https://pathfinderwiki.com/wiki/Template:Paizo_store/data?action=raw").toURL(),
			WikInfo.class
		);
		
		File out = new File("data/store_images");
		out.mkdirs();
		var existingSkus = new HashSet<>();
		Arrays.asList(out.listFiles()).forEach(f->{
			if(f.isFile()) {
				String name = f.getName();
				name = name.substring(0, name.lastIndexOf("."));
				existingSkus.add(name);
			}
		});
		
		MyPool pool = new MyPool("StoreImages");
		for(var productInfo:wikiInfo.values().values()) {
			pool.submit(()-> {
				var product = productInfo.properties;
				try {
					if(product.storeImage == null || existingSkus.contains(product.sku)) return null;
					
					byte[] bytes = Jsoup.connect(product.storeImage).maxBodySize(0).ignoreContentType(true).execute().bodyAsBytes();
					if(bytes.length<10 || Bytes.indexOf(bytes, ImageDownloader.HTML_INDICATOR)!=-1) {
						log.error("{} results in an illegal image file", product.sku);
						return null;
					}
					String inputExtension = FilenameUtils.getExtension(product.storeImage);
					var tmp = Files.createTempFile("", "."+inputExtension);
					Files.write(tmp, bytes);
					var optimized = ImageOptimizer.optimize(tmp.toFile());
					
					var imgFile = new File(out, product.sku+"."+optimized.optimizedExtension());
					Files.move(optimized.result().toPath(), imgFile.toPath());
					return null;
				} catch(Exception e) {
					throw new RuntimeException("Failed for " + product.sku, e);
				}
			});
		}
		pool.shutdown();
	}
}
