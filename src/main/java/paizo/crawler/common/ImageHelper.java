package paizo.crawler.common;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

public class ImageHelper {
	public static boolean usesTransparency(BufferedImage img) {
		var colorModel = img.getColorModel();
		Object pixel = null;
		if(!colorModel.hasAlpha()) return false;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
            	pixel = img.getRaster().getDataElements(x, y, pixel);
                if (colorModel.getAlpha(pixel) < 255){
                    return true;
                }
            }
        }
        return false;
	}

	public static BufferedImage normalizeForHashing(BufferedImage img) {
		//normalize transparency and color space 
		BufferedImage normal = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
		var g=normal.getGraphics();
		g.drawImage(img, 0, 0, Color.WHITE, null);
		g.dispose();
		
		return normal;
	}
}
