package paizo.crawler.s06imageoptimizer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.ImageHelper;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.BlogImage;
import paizo.crawler.common.model.BlogPost;

@RequiredArgsConstructor
public class ImageOptimizer implements PBICallable {

	public static void main(String... args) throws InterruptedException, IOException {
		var pool = new MyPool("Image Optimizer");
		for(var f:new File("blog_posts_details").listFiles()) {
			BlogPost post = Jackson.BLOG_READER.readValue(f);
			if(post.getImages() == null || post.getImages().isEmpty())
				continue;

			pool.submit(new ImageOptimizer(post));
		}
		pool.shutdown();
	}

	private final BlogPost post;

	@Override
	public String getBlogId() {
		return post.getId();
	}

	@Override
	public void run() throws Exception {
		boolean changed = false;
		for(var img : post.getImages()) {
			if(img.getLocalFile() != null && img.getLocalFileAsFile().isFile()) continue;
			File file = Path.of("blog_post_images", img.getName()).toFile();
			if(!file.isFile()) continue;
			
			
			var bytePath = img.getFullPath().getBytes(StandardCharsets.UTF_8);
			ArrayUtils.reverse(bytePath);
			var name = UUID.randomUUID().toString()+".webp";
			img.setLocalFile(Path.of("blog_post_images", name.substring(34,36), name).toString());
			changed = true;
			optimize(file, img);
		}
		
		if(changed) {
			Jackson.BLOG_WRITER.writeValue(post.detailsFile(), post);
		}
	}

	private void optimize(File file, BlogImage img) throws IOException {
		var buffered = ImageIO.read(file);
		if(buffered == null) {
			throw new IllegalStateException("Can't load file "+file);
		}
		img.setUsesTransparency(ImageHelper.usesTransparency(buffered));
		var initialSize = file.length();
		var result = ImmutableImage.wrapAwt(buffered);
		boolean lossless = file.getName().toLowerCase().endsWith(".png") || file.getName().toLowerCase().endsWith(".gif");
		var writer = new WebpWriter(
			9,
			lossless?100:80,
			6,
			lossless,
			!img.getUsesTransparency()
		);
		img.getLocalFileAsFile().getParentFile().mkdirs();
		result.output(writer, img.getLocalFile());
		var newSize = img.getLocalFileAsFile().length();
		if(newSize < initialSize) {
			file.delete();
		}
		else {
			img.getLocalFileAsFile().delete();
			img.setLocalFile(new File(
				img.getLocalFileAsFile().getParentFile(),
				FilenameUtils.removeExtension(img.getLocalFile())+"."+FilenameUtils.getExtension(file.getName())
			).toString());
			FileUtils.moveFile(file, img.getLocalFileAsFile());
		}
	}
}
