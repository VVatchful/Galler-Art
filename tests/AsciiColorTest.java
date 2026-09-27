import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class AsciiColorTest {
    public static void main(String[] args) {
        BufferedImage primary = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xffff0000, 0xff00ff00, 0xff0000ff};
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) primary.setRGB(x, y, colors[x]);
        }
        ImageInspect.AsciiResult result = convert(primary, 3);
        for (int x = 0; x < 3; x++) check(result.rgbAt(x, 0) == (colors[x] & 0xffffff), "RGB channel order");
        check(result.text.equals(ImageInspect.toAscii(primary, 3, false, 0.02, false)), "Same grayscale characters");
        String html = result.toHtml(true);
        check(html.contains("#ff0000") && html.contains("#00ff00") && html.contains("#0000ff"), "HTML colors");
        check(!result.toHtml(false).contains("<span"), "Grayscale HTML");

        BufferedImage mix = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            mix.setRGB(0, y, 0xffff0000);
            mix.setRGB(1, y, 0xff0000ff);
        }
        check(convert(mix, 1).rgbAt(0, 0) == 0x800080, "Average every pixel in each cell");
        mix.setRGB(0, 0, 0x80ff0000);
        mix.setRGB(1, 0, 0x00ffffff);
        mix.setRGB(0, 1, 0x80ff0000);
        mix.setRGB(1, 1, 0x00ffffff);
        check(convert(mix, 1).rgbAt(0, 0) == 0x400000, "Alpha compositing on black");

        ImageInspect.AsciiResult padded = ImageInspect.convert(primary, 30, false, 0.02, false,
                ImageInspect.OutputFormat.SQUARE);
        check(padded.rgbAt(0, 0) == 0 && padded.text.charAt(0) == ' ', "Padding remains blank");

        AsciiDashboard.FittedPreview panel = new AsciiDashboard.FittedPreview();
        panel.setAscii(result.text);
        panel.setColors(result);
        for (boolean fit : new boolean[] {true, false}) {
            panel.fit = fit;
            panel.setSize(300, 200);
            BufferedImage painted = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = painted.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            boolean red = false, green = false, blue = false;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 300; x++) {
                    Color pixel = new Color(painted.getRGB(x, y));
                    red |= pixel.getRed() > 0 && pixel.getGreen() == 0 && pixel.getBlue() == 0;
                    green |= pixel.getGreen() > 0 && pixel.getRed() == 0 && pixel.getBlue() == 0;
                    blue |= pixel.getBlue() > 0 && pixel.getRed() == 0 && pixel.getGreen() == 0;
                }
            }
            check(red && green && blue, "Both preview modes paint RGB glyphs");
        }
        System.out.println("Color checks passed: RGB, averaging, alpha, HTML, padding, both previews.");
    }

    private static ImageInspect.AsciiResult convert(BufferedImage image, int columns) {
        return ImageInspect.convert(image, columns, false, 0.02, false, ImageInspect.OutputFormat.ORIGINAL);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
