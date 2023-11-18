package paizo.crawler.s05imagedownloader;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.function.UnaryOperator;

public class Cropper {

    public static BufferedImage crop(BufferedImage img) {
        if(img == null) {
            return null;
        }
        var cm = img.getColorModel();

        img = whileChanging(img, i->cropTop(cm, i));
        img = whileChanging(img, i->cropBottom(cm, i));
        img = whileChanging(img, i->cropLeft(cm, i));
        img = whileChanging(img, i->cropRight(cm, i));
        return img;
    }

    private static BufferedImage whileChanging(BufferedImage in, UnaryOperator<BufferedImage> changer) {
        boolean run = true;
        BufferedImage result = in;
        while(run) {
            result = changer.apply(in);
            if(result != in) {
                run = true;
                in = result;
            }
            else {
                run = false;
            }
        }
        return result;
    }

    private static BufferedImage cropTop(ColorModel cm, BufferedImage img) {
        int limit = img.getWidth();
        int y = 0;
        int col = img.getRGB(0, y);

        for(int x=1;x<limit;x++) {
            if(img.getRGB(x, y) != col) {
                return img;
            }
        }
        return img.getSubimage(0, 1, img.getWidth(), img.getHeight()-1);
    }

    private static BufferedImage cropBottom(ColorModel cm, BufferedImage img) {
        int limit = img.getWidth();
        int y = img.getHeight() - 1;
        int col = img.getRGB(0, y);

        for(int x=1;x<limit;x++) {
            if(img.getRGB(x, y) != col) {
                return img;
            }
        }
        return img.getSubimage(0, 0, img.getWidth(), img.getHeight()-1);
    }

    private static BufferedImage cropLeft(ColorModel cm, BufferedImage img) {
        int limit = img.getHeight();
        int x = 0;
        int col = img.getRGB(x, 0);

        for(int y=1;y<limit;y++) {
            if(img.getRGB(x, y) != col) {
                return img;
            }
        }
        return img.getSubimage(1, 0, img.getWidth()-1, img.getHeight());
    }

    private static BufferedImage cropRight(ColorModel cm, BufferedImage img) {
        int limit = img.getHeight();
        int x = img.getWidth()-1;
        int col = img.getRGB(x, 0);

        for(int y=1;y<limit;y++) {
            if(img.getRGB(x, y) != col) {
                return img;
            }
        }
        return img.getSubimage(0, 0, img.getWidth()-1, img.getHeight());
    }

}
