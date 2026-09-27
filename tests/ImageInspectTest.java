import java.awt.image.BufferedImage;

public class ImageInspectTest {
    public static void main(String[] args) {
        BufferedImage gradient = new BufferedImage(100, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            int gray = 100 + x / 2;
            for (int y = 0; y < 2; y++) {
                gradient.setRGB(x, y, gray * 0x010101);
            }
        }
        String output = ImageInspect.toAscii(gradient, 100);
        check(output.length() == 101 && output.endsWith("\n"), "Output dimensions");
        check(output.charAt(0) == ' ' && output.charAt(99) == '@',
                "Narrow grayscale range must span the full character ramp");
        String natural = ImageInspect.toAscii(gradient, 100, false, 0.02, false);
        check(natural.charAt(0) != ' ' && natural.charAt(99) != '@',
                "Disabling contrast preserves the narrow brightness range");
        String inverted = ImageInspect.toAscii(gradient, 100, true, 0.02, true);
        check(inverted.charAt(0) == '@' && inverted.charAt(99) == ' ', "Brightness inversion");
        try {
            ImageInspect.toAscii(gradient, 100, true, 0.5, false);
            throw new AssertionError("Invalid clipping must be rejected");
        } catch (IllegalArgumentException expected) {
            // The range must leave at least one brightness level.
        }
        String ramp = " .:-=+*#%@";
        for (int x = 1; x < 100; x++) {
            check(ramp.indexOf(output.charAt(x)) >= ramp.indexOf(output.charAt(x - 1)),
                    "Increasing brightness must not become darker");
        }

        // Isolated black/white pixels should not set the contrast range.
        gradient.setRGB(0, 0, 0);
        gradient.setRGB(0, 1, 0);
        gradient.setRGB(99, 0, 0xffffff);
        gradient.setRGB(99, 1, 0xffffff);
        output = ImageInspect.toAscii(gradient, 100);
        check(output.charAt(2) == ' ' && output.charAt(97) == '@', "Outlier clipping");

        BufferedImage flat = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        check(ImageInspect.toAscii(flat, 1).equals(" \n"), "Transparent pixel");
        flat.setRGB(0, 0, 0xff000000);
        check(ImageInspect.toAscii(flat, 1).equals(" \n"), "Solid black");
        flat.setRGB(0, 0, 0xffffffff);
        check(ImageInspect.toAscii(flat, 1).equals("@\n"), "Solid white");
        flat.setRGB(0, 0, 0xff808080);
        check(ImageInspect.toAscii(flat, 1).equals("+\n"), "Flat gray stays gray");
        BufferedImage wide = new BufferedImage(2560, 1040, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = wide.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, wide.getWidth(), wide.getHeight());
        graphics.dispose();
        for (ImageInspect.OutputFormat format : ImageInspect.OutputFormat.values()) {
            int[] size = ImageInspect.outputSize(wide, format.columns, format);
            String formatted = ImageInspect.toAscii(wide, format.columns, true, 0.02, false, format);
            String[] rows = formatted.split("\n");
            check(rows.length == size[1], "Preset row count: " + format);
            for (String row : rows) check(row.length() == size[0], "Preset column count: " + format);
            check(formatted.contains("@"), "Image survives fitting: " + format);
        }
        String[] hd = ImageInspect.toAscii(wide, 160, true, 0.02, false,
                ImageInspect.OutputFormat.HD).split("\n");
        check(hd.length == 45 && hd[0].isBlank() && hd[44].isBlank(), "Wide image is letterboxed");
        check(hd[22].equals("@".repeat(160)), "Full width remains visible");
        BufferedImage tall = new BufferedImage(250, 1040, BufferedImage.TYPE_INT_RGB);
        graphics = tall.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, 250, 1040);
        graphics.dispose();
        String[] portraitFit = ImageInspect.toAscii(tall, 160, true, 0.02, false,
                ImageInspect.OutputFormat.HD).split("\n");
        check(portraitFit[0].contains("@") && portraitFit[44].contains("@"), "Tall image keeps top and bottom");
        check(portraitFit[22].startsWith(" ") && portraitFit[22].endsWith(" "), "Tall image gets side padding");
        String enlarged = ImageInspect.toAscii(flat, 80, true, 0.02, false, ImageInspect.OutputFormat.SD);
        check(enlarged.contains("+"), "Tiny images can be enlarged without empty samples");
        System.out.println("All grayscale checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
