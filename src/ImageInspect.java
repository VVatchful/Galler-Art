import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ImageInspect {
    public enum OutputFormat {
        ORIGINAL("Keep original proportions", 0, 0, 160),
        SD("SD - 640 x 480 (4:3)", 640, 480, 80),
        HD("HD - 1280 x 720 (16:9)", 1280, 720, 160),
        FULL_HD("Full HD - 1920 x 1080 (16:9)", 1920, 1080, 240),
        QHD("QHD - 2560 x 1440 (16:9)", 2560, 1440, 320),
        UHD("4K - 3840 x 2160 (16:9)", 3840, 2160, 480),
        SQUARE("Square - 1080 x 1080 (1:1)", 1080, 1080, 135),
        PORTRAIT("Portrait - 1080 x 1350 (4:5)", 1080, 1350, 135),
        VERTICAL("Vertical - 1080 x 1920 (9:16)", 1080, 1920, 135);

        final String label;
        final int pixelWidth;
        final int pixelHeight;
        final int columns;

        OutputFormat(String label, int pixelWidth, int pixelHeight, int columns) {
            this.label = label;
            this.pixelWidth = pixelWidth;
            this.pixelHeight = pixelHeight;
            this.columns = columns;
        }

        public String toString() { return label; }
    }

    // Cells are modeled as twice as tall as they are wide, as in the original converter.
    public static int[] outputSize(BufferedImage image, int columns, OutputFormat format) {
        if (columns < 1) throw new IllegalArgumentException("Columns must be positive.");
        int width = format == OutputFormat.ORIGINAL ? Math.min(columns, image.getWidth()) : columns;
        double ratio = format == OutputFormat.ORIGINAL
                ? (double) image.getHeight() / image.getWidth()
                : (double) format.pixelHeight / format.pixelWidth;
        return new int[] {width, Math.max(1, (int) Math.round(width * ratio * 0.5))};
    }

    private static final String CHARACTERS = " .:-=+*#%@";
    private static final double CONTRAST_CLIP = 0.02;

    public static final class AsciiResult {
        public final String text;
        public final int width;
        public final int height;
        private final int[] colors;

        private AsciiResult(String text, int width, int height, int[] colors) {
            this.text = text;
            this.width = width;
            this.height = height;
            this.colors = colors;
        }

        public int rgbAt(int x, int y) { return colors[y * width + x]; }

        public String toHtml(boolean color) {
            StringBuilder html = new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                    + "<title>Galler-Art ASCII</title><style>body{background:#000;color:#e8edf4;margin:24px}"
                    + "pre{font:16px monospace;line-height:2ch;white-space:pre}"
                    + "</style><body><pre aria-label=\"ASCII art\">");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    char character = text.charAt(y * (width + 1) + x);
                    if (color && character != ' ') {
                        String hex = Integer.toHexString(rgbAt(x, y));
                        html.append("<span style=\"color:#").append("0".repeat(6 - hex.length()))
                                .append(hex).append("\">");
                    }
                    if (character == '&') html.append("&amp;");
                    else if (character == '<') html.append("&lt;");
                    else if (character == '>') html.append("&gt;");
                    else html.append(character);
                    if (color && character != ' ') html.append("</span>");
                }
                html.append('\n');
            }
            return html.append("</pre></body></html>").toString();
        }
    }

    public static void main(String[] args) throws IOException {
        File imagefile = new File(args.length > 0 ? args[0] : "Images/img.png");
        if (!imagefile.isFile()) {
            System.err.println("Image file not found: " + imagefile);
            return;
        }
        BufferedImage image = ImageIO.read(imagefile);
        if (image == null) {
            System.err.println("Unsupported image format: " + imagefile);
            return;
        }
        int columns = args.length > 1 ? Integer.parseInt(args[1]) : 160;
        String ascii = toAscii(image, columns);
        Path outputDirectory = Path.of("ascii");
        Files.createDirectories(outputDirectory);
        String filename = imagefile.getName();
        int extensionIndex = filename.lastIndexOf('.');
        String basename = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        Path outputFile = outputDirectory.resolve(basename + ".txt");
        Files.writeString(outputFile, ascii, StandardCharsets.UTF_8);
        System.out.print(ascii);
        System.out.println("ASCII saved to: " + outputFile.toAbsolutePath());
    }

    public static String toAscii(BufferedImage image, int columns) {
        return toAscii(image, columns, true, CONTRAST_CLIP, false);
    }

    public static String toAscii(BufferedImage image, int columns, boolean autoContrast,
                                 double contrastClip, boolean invert) {
        return toAscii(image, columns, autoContrast, contrastClip, invert, OutputFormat.ORIGINAL);
    }

    public static String toAscii(BufferedImage image, int columns, boolean autoContrast,
                                 double contrastClip, boolean invert, OutputFormat format) {
        return convert(image, columns, autoContrast, contrastClip, invert, format).text;
    }

    public static AsciiResult convert(BufferedImage image, int columns, boolean autoContrast,
                                      double contrastClip, boolean invert, OutputFormat format) {
        if (columns < 1) {
            throw new IllegalArgumentException("Columns must be positive.");
        }
        if (!Double.isFinite(contrastClip) || contrastClip < 0 || contrastClip >= 0.5) {
            throw new IllegalArgumentException("Contrast clipping must be between 0 and 0.5 (exclusive).");
        }
        int[] canvas = outputSize(image, columns, format);
        double scale = Math.min((double) canvas[0] / image.getWidth(),
                canvas[1] * 2.0 / image.getHeight());
        int width = Math.max(1, Math.min(canvas[0], (int) Math.round(image.getWidth() * scale)));
        int height = Math.max(1, Math.min(canvas[1], (int) Math.round(image.getHeight() * scale * 0.5)));
        if (format == OutputFormat.ORIGINAL) {
            width = canvas[0];
            height = canvas[1];
        }
        double[] brightnessValues = new double[Math.multiplyExact(width, height)];
        int[] cellColors = new int[brightnessValues.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int startX = (int) ((long) x * image.getWidth() / width);
                int endX = (int) ((long) (x + 1) * image.getWidth() / width);
                int startY = (int) ((long) y * image.getHeight() / height);
                int endY = (int) ((long) (y + 1) * image.getHeight() / height);
                // A preset can enlarge a tiny image; sample at least one source pixel per cell.
                endX = Math.max(startX + 1, endX);
                endY = Math.max(startY + 1, endY);
                double totalBrightness = 0;
                double totalRed = 0, totalGreen = 0, totalBlue = 0;
                // Include every pixel in this cell, including the image's outer edges.
                for (int sourceY = startY; sourceY < endY; sourceY++) {
                    for (int sourceX = startX; sourceX < endX; sourceX++) {
                        int pixel = image.getRGB(sourceX, sourceY);
                        int red = (pixel >>> 16) & 255;
                        int green = (pixel >>> 8) & 255;
                        int blue = pixel & 255;
                        double alpha = ((pixel >>> 24) & 255) / 255.0;
                        // Perceived brightness on a black background.
                        totalBrightness += (0.2126 * red + 0.7152 * green + 0.0722 * blue) * alpha;
                        totalRed += red * alpha;
                        totalGreen += green * alpha;
                        totalBlue += blue * alpha;
                    }
                }
                brightnessValues[y * width + x] = totalBrightness
                        / ((long) (endX - startX) * (endY - startY));
                long count = (long) (endX - startX) * (endY - startY);
                cellColors[y * width + x] = ((int) Math.round(totalRed / count) << 16)
                        | ((int) Math.round(totalGreen / count) << 8)
                        | (int) Math.round(totalBlue / count);
            }
        }

        double blackPoint = 0;
        double whitePoint = 255;
        if (autoContrast) {
            // Clip isolated extremes before stretching the brightness range.
            double[] sorted = brightnessValues.clone();
            Arrays.sort(sorted);
            int clipCount = (int) Math.floor(sorted.length * contrastClip);
            blackPoint = sorted[clipCount];
            whitePoint = sorted[sorted.length - 1 - clipCount];
        }
        // Preserve flat images and avoid amplifying sub-level rounding noise.
        if (whitePoint - blackPoint < 1.0) {
            blackPoint = 0;
            whitePoint = 255;
        }

        StringBuilder result = new StringBuilder();
        int[] outputColors = new int[Math.multiplyExact(canvas[0], canvas[1])];
        int left = (canvas[0] - width) / 2;
        int top = (canvas[1] - height) / 2;
        // Padding is added after contrast calculation so it cannot change the grayscale range.
        for (int canvasY = 0; canvasY < canvas[1]; canvasY++) {
            for (int canvasX = 0; canvasX < canvas[0]; canvasX++) {
                int x = canvasX - left;
                int y = canvasY - top;
                if (x < 0 || x >= width || y < 0 || y >= height) {
                    result.append(' ');
                    continue;
                }
                double normalized = (brightnessValues[y * width + x] - blackPoint)
                        / (whitePoint - blackPoint);
                normalized = Math.max(0, Math.min(1, normalized));
                if (invert) {
                    normalized = 1 - normalized;
                }
                int index = (int) Math.round(normalized * (CHARACTERS.length() - 1));
                result.append(CHARACTERS.charAt(index));
                outputColors[canvasY * canvas[0] + canvasX] = cellColors[y * width + x];
            }
            result.append('\n');
        }
        return new AsciiResult(result.toString(), canvas[0], canvas[1], outputColors);
    }
}
