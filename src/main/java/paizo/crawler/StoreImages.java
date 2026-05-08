package paizo.crawler;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
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
	
	private static record ImageMappings(TreeMap<String, ImageMapping> map) {}
	private static record ImageMapping(String sku, String url, String optimizedImage) {}
	
	public static void main(String[] args) throws Exception {
		var wikiInfo = Jackson.JSON.readValue(
			URI.create("https://pathfinderwiki.com/wiki/Template:Paizo_store/data?action=raw").toURL(),
			WikInfo.class
		);
		File cfg = new File("data/store_image_mapping.yaml");
		var mappings = Jackson.MAPPER.readValue(cfg, ImageMappings.class);
		File out = new File("data/store_images");
		out.mkdirs();
		
		MyPool pool = new MyPool("StoreImages");
		var results = wikiInfo.values().values()
			.stream()
			.map(productInfo->
				pool.submit(()-> {
					var product = productInfo.properties;
					try {
						if(product.storeImage == null) return null;
						var oldMapping = mappings.map.get(product.sku);
						if(oldMapping != null && oldMapping.url.equals(product.storeImage)) return oldMapping;
						
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
						FileUtils.deleteQuietly(imgFile);
						Files.move(optimized.result().toPath(), imgFile.toPath());
						return new ImageMapping(product.sku, product.storeImage, imgFile.getName());
					} catch(Exception e) {
						throw new RuntimeException("Failed for " + product.sku, e);
					}
				})
			).toList();
		pool.shutdown();
		
		results.forEach(f-> {
			try {
				var r = f.get();
				if(r != null)
					mappings.map.put(r.sku, r);
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			
		});
		
		Jackson.MAPPER.writeValue(cfg, mappings);
		
		for(var f:out.listFiles()) {
			if(mappings.map.values().stream().noneMatch(m->Objects.equals(m.optimizedImage, f.getName()))) {
				log.info("Deleting outdated {}", f.getName());
				FileUtils.deleteQuietly(f);
			}
		}
	}
}
