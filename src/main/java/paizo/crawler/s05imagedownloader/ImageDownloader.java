package paizo.crawler.s05imagedownloader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.databind.DatabindException;
import com.google.common.hash.Hashing;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.ImageInfo;

@RequiredArgsConstructor
public class ImageDownloader implements PBICallable {

	public static void main(String... args) throws InterruptedException, IOException {
		var pool = new MyPool("Image Downloader");
		for(var f:new File("data/blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null)
				continue;

			for(BlogImage img : post.getImages()) {
				pool.submit(new ImageDownloader(img));
			}

		}
		pool.shutdown();
	}

	private final BlogImage img;

	@Override
	public String getBlogId() {
		return img.getName();
	}

	
	@Override
	public void run() throws Exception {
		var info = loadImageInfo(img.getFullPath());
		if(info.isDownloaded()) {
			return;
		}

		byte[] bytes = Jsoup.connect(img.getFullPath()).maxBodySize(0).ignoreContentType(true).execute().bodyAsBytes();
		var extension = FilenameUtils.getExtension(img.getName());
		info.setRawExtension(extension);
		FileUtils.writeByteArrayToFile(info.getRawFile(), bytes);
		Jackson.MAPPER.writeValue(infoFile(info.getId()), info);
	}
	
	public synchronized static ImageInfo loadImageInfo(String fullURL) throws IOException, DatabindException, IOException {
		var potentialId = potentialImageId(fullURL);
		
		for(int varCounter=1;true;varCounter++) {
			var id = potentialId+(varCounter==1?"":("-"+varCounter));
			File infoFile = infoFile(id);
			if(!infoFile.isFile()) {
				var result = new ImageInfo();
				result.setFullPath(fullURL);
				result.setId(id);
				infoFile.getParentFile().mkdirs();
				Jackson.MAPPER.writeValue(infoFile, result);
				return result;
			}
			
			var info = Jackson.MAPPER.readValue(infoFile, ImageInfo.class);
			if(fullURL.equals(info.getFullPath())) {
				return info;
			}
		}
	}
	
	public static File infoDir(String id) {
		return Path.of(
			"data",
			"images",
			id.substring(0,1),
			id.substring(1)
		).toFile();
	}
	
	public static File infoFile(String id) {
		return new File(infoDir(id), "info.yaml");
	}
	
	public static String potentialImageId(String fullURL) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			Hashing.murmur3_128()
				.hashBytes(fullURL.getBytes(StandardCharsets.UTF_8))
				.asBytes()
		);
	}

	
}
