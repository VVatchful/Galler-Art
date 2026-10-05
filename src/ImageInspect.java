import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ImageInspect {
    public static final class ImageFrame {
        /** Independent, fully composed logical-screen image, before disposal. */
        public final BufferedImage image;
        public final int delayMillis;

        private ImageFrame(BufferedImage image, int delayMillis) {
            this.image = image;
            this.delayMillis = delayMillis;
        }
    }

    /** Reads still images or all GIF frames in display order. */
    public static List<ImageFrame> readFrames(File file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Cannot open image: " + file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("This file is not a supported image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("gif")) {
                    return List.of(new ImageFrame(reader.read(0), 0));
                }
                Node stream = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                Node screen = child(stream, "LogicalScreenDescriptor");
                int width = number(screen, "logicalScreenWidth", reader.getWidth(0));
                int height = number(screen, "logicalScreenHeight", reader.getHeight(0));
                if (width < 1 || height < 1) throw new IOException("Invalid GIF canvas dimensions.");
                int background = gifBackground(stream);
                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                List<ImageFrame> frames = new ArrayList<>();
                int count = reader.getNumImages(true);
                for (int i = 0; i < count; i++) {
                    Node metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    Node descriptor = child(metadata, "ImageDescriptor");
                    Node control = child(metadata, "GraphicControlExtension");
                    int left = number(descriptor, "imageLeftPosition", 0);
                    int top = number(descriptor, "imageTopPosition", 0);
                    String disposal = attribute(control, "disposalMethod", "none");
                    boolean transparent = "TRUE".equalsIgnoreCase(attribute(control, "transparentColorFlag", "FALSE"));
                    // Transparent GIFs use a transparent canvas, later composited onto black by ASCII conversion.
                    int clearColor = transparent ? 0 : background;
                    if (i == 0) fill(canvas, 0, 0, width, height, clearColor);
                    BufferedImage previous = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                    BufferedImage patch = reader.read(i);
                    Graphics2D graphics = canvas.createGraphics();
                    try {
                        graphics.drawImage(patch, left, top, null);
                    } finally {
                        graphics.dispose();
                    }
                    frames.add(new ImageFrame(copy(canvas), number(control, "delayTime", 0) * 10));
                    // Disposal applies after capturing this frame, before drawing the next one.
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        fill(canvas, left, top, patch.getWidth(), patch.getHeight(), clearColor);
                    } else if (previous != null) {
                        canvas = previous;
                    }
                }
                if (frames.isEmpty()) throw new IOException("The GIF contains no frames.");
                return List.copyOf(frames);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int gifBackground(Node stream) {
        Node table = child(stream, "GlobalColorTable");
        int index = number(table, "backgroundColorIndex", -1);
        for (Node entry = table == null ? null : table.getFirstChild(); entry != null; entry = entry.getNextSibling()) {
            if ("ColorTableEntry".equals(entry.getNodeName()) && number(entry, "index", -2) == index) {
                return 0xff000000 | (number(entry, "red", 0) << 16)
                        | (number(entry, "green", 0) << 8) | number(entry, "blue", 0);
            }
        }
        return 0;
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent == null ? null : parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) return node;
        }
        return null;
    }

    private static String attribute(Node node, String name, String fallback) {
        Node value = node == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }

    private static int number(Node node, String name, int fallback) {
        return Integer.parseInt(attribute(node, name, Integer.toString(fallback)));
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(argb, true));
            graphics.fillRect(x, y, width, height);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        result.setData(image.getData());
        return result;
    }

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

    public enum ExportFormat {
        TXT("txt"), HTML("html"), ANSI("ans"), BBCODE("bbcode"), MARKDOWN("md"), SVG("svg");
        public final String extension;
        ExportFormat(String extension) { this.extension = extension; }
    }

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

        public String export(ExportFormat format, boolean color) {
            switch (format) {
                case TXT: return text;
                case HTML: return toHtml(color);
                case ANSI: return toAnsi(color);
                case BBCODE: return toBbcode(color);
                case MARKDOWN: return toMarkdown(color);
                case SVG: return toSvg(color);
                default: throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        }

        private static String hex(int rgb) {
            return String.format(java.util.Locale.ROOT, "%06x", rgb);
        }

        private static String escaped(char c) {
            switch (c) {
                case '&': return "&amp;";
                case '<': return "&lt;";
                case '>': return "&gt;";
                case '"': return "&quot;";
                case '\'': return "&#39;";
                default: return String.valueOf(c);
            }
        }

        public String toAnsi(boolean color) {
            StringBuilder ansi = new StringBuilder();
            for (int y = 0; y < height; y++) {
                // Establish black background and a readable foreground independently of terminal theme.
                ansi.append("\u001b[0;40;97m");
                int previous = -1;
                for (int x = 0; x < width; x++) {
                    char c = text.charAt(y * (width + 1) + x);
                    int rgb = rgbAt(x, y);
                    if (color && c != ' ' && rgb != previous) {
                        ansi.append("\u001b[38;2;").append((rgb >>> 16) & 255).append(';')
                                .append((rgb >>> 8) & 255).append(';').append(rgb & 255).append('m');
                        previous = rgb;
                    }
                    ansi.append(c);
                }
                ansi.append("\u001b[0m\n");
            }
            return ansi.toString();
        }

        public String toBbcode(boolean color) {
            StringBuilder bb = new StringBuilder("[pre][font=monospace]");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    char c = text.charAt(y * (width + 1) + x);
                    if (color && c != ' ') bb.append("[color=#").append(hex(rgbAt(x, y))).append(']');
                    bb.append(c); // The fixed ASCII ramp contains no BBCode delimiters.
                    if (color && c != ' ') bb.append("[/color]");
                }
                bb.append('\n');
            }
            return bb.append("[/font][/pre]\n").toString();
        }

        public String toMarkdown(boolean color) {
            if (!color) return "```text\n" + text + "```\n";
            String html = toHtml(true);
            int start = html.indexOf('>', html.indexOf("<pre ")) + 1;
            return "<pre style=\"background:#000;color:#e8edf4;font-family:monospace;font-size:16px;"
                    + "line-height:2ch;white-space:pre;overflow:auto\">"
                    + html.substring(start, html.indexOf("</pre>")) + "</pre>\n";
        }

        public String toSvg(boolean color) {
            long pixelWidth = (long) width * 8;
            long pixelHeight = (long) height * 16;
            StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                    .append(pixelWidth).append(' ').append(pixelHeight).append("\" width=\"")
                    .append(pixelWidth).append("\" height=\"").append(pixelHeight)
                    .append("\" role=\"img\" aria-label=\"ASCII art\">\n<title>Galler-Art ASCII</title>\n")
                    .append("<rect width=\"100%\" height=\"100%\" fill=\"#000\"/>\n")
                    .append("<g font-family=\"monospace\" font-size=\"13\" font-variant-ligatures=\"none\" xml:space=\"preserve\">\n");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    char c = text.charAt(y * (width + 1) + x);
                    if (c == ' ') continue; // Explicit coordinates preserve empty cells and padding.
                    svg.append("<text x=\"").append((long) x * 8).append("\" y=\"").append((long) y * 16 + 13)
                            .append("\" fill=\"#").append(color ? hex(rgbAt(x, y)) : "e8edf4")
                            .append("\">").append(escaped(c)).append("</text>\n");
                }
            }
            return svg.append("</g>\n</svg>\n").toString();
        }

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
        List<ImageFrame> frames = readFrames(imagefile);
        int columns = args.length > 1 ? Integer.parseInt(args[1]) : 160;
        Path outputDirectory = Path.of("ascii");
        Files.createDirectories(outputDirectory);
        String filename = imagefile.getName();
        int extensionIndex = filename.lastIndexOf('.');
        String basename = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        for (int i = 0; i < frames.size(); i++) {
            String ascii = toAscii(frames.get(i).image, columns);
            String suffix = frames.size() > 1 ? String.format("-frame-%04d", i + 1) : "";
            Path outputFile = outputDirectory.resolve(basename + suffix + ".txt");
            Files.writeString(outputFile, ascii, StandardCharsets.UTF_8);
            System.out.print(ascii);
            System.out.println("ASCII saved to: " + outputFile.toAbsolutePath());
        }
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
