package paizo.crawler.s05imagedownloader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.databind.DatabindException;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Bytes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;
import paizo.crawler.common.model.ImageInfo;

@Slf4j
@RequiredArgsConstructor
public class ImageDownloader implements PBICallable {

	public static void main(String... args) throws InterruptedException, IOException {
		var pool = new MyPool("Image Downloader");
		var posts = new ArrayList<BlogPost>();
		for(var f:new File("data/blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			posts.add(post);
			if(post.getImages() == null)
				continue;

			for(BlogImage img : post.getImages()) {
				pool.submit(new ImageDownloader(post, img));
			}

		}
		pool.shutdown();
		for(var post:posts) {
			if(post.isChanged()) {
				Jackson.MAPPER.writeValue(post.detailsFile(), post);
			}
		}
	}

	private final BlogPost post;
	private final BlogImage img;

	@Override
	public String getBlogId() {
		return img.getCandidatePaths().get(0);
	}

	private static final byte[] HTML_INDICATOR = "!doctype html".getBytes(StandardCharsets.UTF_8); 
	
	@Override
	public void run() throws Exception {
		var info = loadImageInfo(img.getCandidatePaths());
		if(!info.getId().equals(img.getId())) {
			img.setId(info.getId());
			synchronized(post) {
				post.setChanged(true);
			}
		}
		if(info.isDownloaded()) {
			return;
		}

		byte[] biggest = null;
		String best = null;
		for(var path:img.getCandidatePaths()) {
			byte[] bytes = Jsoup.connect(path).maxBodySize(0).ignoreContentType(true).execute().bodyAsBytes();
			if(bytes.length<10 || Bytes.indexOf(bytes, HTML_INDICATOR)!=-1) {
				log.error("{} results in an illegal image file", path);
				continue;
			}
			if(biggest == null || bytes.length>biggest.length) {
				biggest = bytes;
				best = path;
			}
		}
		
		if(biggest != null) {
			var extension = FilenameUtils.getExtension(best);
			info.setRawExtension(extension);
			info.setSelectedPath(best);
			info.setName(best.substring(best.lastIndexOf("/")+1));
			FileUtils.writeByteArrayToFile(info.getRawFile(), biggest);
			Jackson.MAPPER.writeValue(infoFile(info.getId()), info);
		}
	}
	
	public synchronized static ImageInfo loadImageInfo(List<String> urlSet) throws IOException, DatabindException, IOException {
		urlSet = Lists.newArrayList(urlSet);
		Collections.sort(urlSet);
		var potentialId = potentialImageId(urlSet);
		
		for(int varCounter=1;true;varCounter++) {
			var id = potentialId+(varCounter==1?"":("-"+varCounter));
			File infoFile = infoFile(id);
			if(!infoFile.isFile()) {
				var result = new ImageInfo();
				result.setCandidatePaths(urlSet);
				result.setId(id);
				infoFile.getParentFile().mkdirs();
				Jackson.MAPPER.writeValue(infoFile, result);
				return result;
			}
			
			var info = Jackson.MAPPER.readValue(infoFile, ImageInfo.class);
			if(urlSet.equals(info.getCandidatePaths())) {
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
	
	public static String potentialImageId(List<String> urlSet) {
		var hasher = Hashing.murmur3_32_fixed().newHasher()
			.putInt(urlSet.size());
		for(String url:urlSet) {
			hasher = hasher.putBytes(url.getBytes(StandardCharsets.UTF_8));
		}
		return HexFormat.of().formatHex(hasher.hash().asBytes());
	}

	
}
