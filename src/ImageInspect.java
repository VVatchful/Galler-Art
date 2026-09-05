import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ImageInspect {
    static void main(String[] args ) throws IOException {
        File imagefile = new File("Images/img.png");
        BufferedImage image = ImageIO.read(imagefile);
        if  (image == null) {
            System.out.println("No image found or the corresponding file is not a supported image file.");
            return;
        }
        System.out.println("Width: " + image.getWidth());
        System.out.println("Height: " + image.getHeight());
    }
}
