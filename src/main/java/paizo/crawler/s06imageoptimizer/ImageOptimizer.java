package paizo.crawler.s06imageoptimizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import lombok.RequiredArgsConstructor;
import paizo.crawler.common.ImageHelper;
import paizo.crawler.common.Jackson;
import paizo.crawler.common.MyPool;
import paizo.crawler.common.PBICallable;
import paizo.crawler.common.model.ImageInfo;

@RequiredArgsConstructor
public class ImageOptimizer implements PBICallable {

	public static void main(String... args) throws InterruptedException, IOException {
		var pool = new MyPool("Image Optimizer");
		for(var d:new File("data/images").listFiles()) {
			for(var f:d.listFiles()) {
				var info = Jackson.MAPPER.readValue(new File(f, "info.yaml"), ImageInfo.class);
				pool.submit(new ImageOptimizer(info));
			}
		}
		pool.shutdown();
	}

	private final ImageInfo info;

	@Override
	public String getBlogId() {
		return info.getId();
	}

	@Override
	public void run() throws Exception {
		var optimizedFile = info.getOptimizedFile();
		if(optimizedFile != null && optimizedFile.isFile()) {
			return;
		}
		
		var rawFile = info.getRawFile();
		if(rawFile == null || !rawFile.isFile()) {
			return;
		}
		
		optimize(rawFile);
		Jackson.MAPPER.writeValue(info.getInfoFile(), info);
	}

	private void optimize(File rawFile) throws IOException {
		var buffered = ImageIO.read(rawFile);
		if(buffered == null) {
			throw new IllegalStateException("Can't load file "+rawFile);
		}
		info.setUsesTransparency(ImageHelper.usesTransparency(buffered));
		
		var cropped = Cropper.crop(buffered);
		if(cropped == null)
			cropped = buffered;
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(cropped, FilenameUtils.getExtension(rawFile.getName()), out);
		var croppedOnly = out.toByteArray();
		
		var webp = ImmutableImage.wrapAwt(cropped);
		boolean lossless = rawFile.getName().toLowerCase().endsWith(".png") || rawFile.getName().toLowerCase().endsWith(".gif");
		var writer = new WebpWriter(
			9,
			lossless?100:80,
			6,
			lossless,
			!info.getUsesTransparency()
		);
		
		var tmpWebp = Files.createTempFile("", ".webp").toFile();
		webp.output(writer, tmpWebp);
		
		long webpSize = tmpWebp.length();
		long croppedSIze = croppedOnly.length;
		
		if(webpSize < croppedSIze) {
			info.setOptimizedExtension("webp");
			FileUtils.moveFile(tmpWebp, info.getOptimizedFile());
			rawFile.delete();
		}
		else {
			tmpWebp.delete();
			info.setOptimizedExtension(FilenameUtils.getExtension(rawFile.getName()));
			FileUtils.writeByteArrayToFile(info.getOptimizedFile(), croppedOnly);
			rawFile.delete();
		}
	}
}
