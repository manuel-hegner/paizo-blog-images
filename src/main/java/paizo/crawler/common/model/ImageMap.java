package paizo.crawler.common.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ImageMap {

	private Map<String, String> url2File = new HashMap<>();
}
