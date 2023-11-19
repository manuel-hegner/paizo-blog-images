package paizo.crawler.common.model;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;

import dev.brachtendorf.jimagehash.hash.Hash;
import lombok.Getter;
import lombok.Setter;
import paizo.crawler.s05imagedownloader.ImageDownloader;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class ImageInfo {

	private String id;
	private String fullPath;
	private String rawExtension;
	private String optimizedExtension;
	private Hash hash;
	private long pixels = -1;
	private WikiMappings wikiMappings = new WikiMappings();
	private Boolean usesTransparency;
	
	@JsonIgnore
	public File getRawFile() {
		if(rawExtension == null)
			return null;
		return new File(ImageDownloader.infoDir(id), "raw."+rawExtension);
	}
	
	@JsonIgnore
	public File getOptimizedFile() {
		if(rawExtension == null)
			return null;
		return new File(ImageDownloader.infoDir(id), "optimized."+optimizedExtension);
	}
	
	@JsonIgnore
	public File getInfoFile() {
		return ImageDownloader.infoFile(this.getId());
	}
	
	@Getter
	@Setter
	public static class WikiMappings {
		private WikiImage pf;
		private WikiImage sf;
		
		@JsonIgnore
		public boolean hasMapping() {
			return pf != null || sf != null;
		}
	}

	@JsonIgnore
	public boolean isDownloaded() {
		var rawFile = getRawFile();
		if(rawFile!=null && rawFile.isFile())
			return true;
		var optimizedFile = getOptimizedFile();
		if(optimizedFile!=null && optimizedFile.isFile())
			return true;
		return false;
	}

	
}
