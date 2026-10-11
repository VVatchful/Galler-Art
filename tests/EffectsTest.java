import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;

public class EffectsTest {
    public static void main(String[] args) throws Exception {
        double[] mid = new double[1024];
        Arrays.fill(mid, 0.3);
        for (ImageInspect.Dither dither : new ImageInspect.Dither[] {ImageInspect.Dither.FLOYD_STEINBERG, ImageInspect.Dither.BAYER}) {
            ImageInspect.Effects effects = new ImageInspect.Effects(dither, ImageInspect.Edges.NONE, " @");
            String result = new String(ImageInspect.quantize(mid, 32, 32, effects));
            double coverage = result.chars().filter(c -> c == '@').count() / 1024.0;
            check(Math.abs(coverage - 0.3) < 0.025, "Halftone preserves average brightness: " + dither);
            check(result.equals(new String(ImageInspect.quantize(mid, 32, 32, effects))), "Deterministic dithering");
            check(new String(ImageInspect.quantize(new double[16], 4, 4, effects)).equals(" ".repeat(16)), "Black remains black");
            double[] white = new double[16]; Arrays.fill(white, 1);
            check(new String(ImageInspect.quantize(white, 4, 4, effects)).equals("@".repeat(16)), "White remains white");
        }
        double[] flat = new double[256]; Arrays.fill(flat, 127.12345);
        double[] step = new double[256];
        for (int y = 0; y < 16; y++) for (int x = 8; x < 16; x++) step[y * 16 + x] = 255;
        long sobelCount = 0;
        for (ImageInspect.Edges edges : new ImageInspect.Edges[] {ImageInspect.Edges.SOBEL, ImageInspect.Edges.CANNY}) {
            check(Arrays.stream(ImageInspect.edgeMap(flat, 16, 16, edges)).allMatch(v -> v == 0), "Flat fields have no edges");
            double[] detected = ImageInspect.edgeMap(step, 16, 16, edges);
            long count = Arrays.stream(detected).filter(v -> v > 0).count();
            check(count > 0, "Step outline detected");
            for (int y = 0; y < 16; y++) check(detected[y * 16] == 0 && detected[y * 16 + 15] == 0, "No artificial canvas border");
            if (edges == ImageInspect.Edges.SOBEL) sobelCount = count;
            else check(count < sobelCount, "Canny suppresses nonmaximal edges");
            check(ImageInspect.edgeMap(new double[] {128}, 1, 1, edges)[0] == 0, "One-cell edge input");
        }
        for (String invalid : new String[] {"", "x", "x\n", "x\u001b", "x\u2588"}) {
            try { new ImageInspect.Effects(ImageInspect.Dither.NONE, ImageInspect.Edges.NONE, invalid); throw new AssertionError("Invalid palette accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) image.setRGB(x, y, (x * 8) * 0x010101);
        ImageInspect.Effects custom = new ImageInspect.Effects(ImageInspect.Dither.BAYER, ImageInspect.Edges.SOBEL, " .oO@XYZ");
        ImageInspect.AsciiResult result = ImageInspect.convert(image, 32, true, 0.02, false, ImageInspect.OutputFormat.ORIGINAL, custom);
        check(result.text.chars().allMatch(c -> c == '\n' || custom.palette.indexOf(c) >= 0), "Only custom glyphs emitted");
        check(result.text.equals(new FrameProcessing.Settings(32, true, 0.02, false, ImageInspect.OutputFormat.ORIGINAL, custom).convert(image).text), "Parallel settings carry effects");
        ImageInspect.AsciiResult markup = ImageInspect.convert(image, 32, false, 0, false, ImageInspect.OutputFormat.ORIGINAL,
                new ImageInspect.Effects(ImageInspect.Dither.NONE, ImageInspect.Edges.NONE, "[]<>&"));
        check(markup.toBbcode(true).contains("&#91;") && markup.toBbcode(true).contains("&amp;"), "BBCode palette delimiters escaped");
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(markup.toSvg(true).getBytes(StandardCharsets.UTF_8)));
        String markdown = markup.toMarkdown(true);
        String restored = factory.newDocumentBuilder().parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8))).getDocumentElement().getTextContent();
        check(restored.equals(markup.text), "Styled markup escaping preserves custom characters");
        BufferedImage white = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) white.setRGB(x, y, 0xffffff);
        ImageInspect.AsciiResult ticks = ImageInspect.convert(white, 3, false, 0, false, ImageInspect.OutputFormat.ORIGINAL,
                new ImageInspect.Effects(ImageInspect.Dither.NONE, ImageInspect.Edges.NONE, " `"));
        check(ticks.toMarkdown(false).startsWith("````text\n"), "Markdown fence longer than custom backtick runs");
        Path input = Files.createTempFile("ascii-effects-", ".png");
        Path config = Files.createTempFile("ascii-effects-", ".yaml");
        try {
            ImageIO.write(image, "png", input.toFile());
            Files.writeString(config, "columns: 32\ndither: bayer\nedges: sobel\npalette: ' .oO@XYZ'\n");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int code = AsciiCli.run(new String[] {input.toString(), "--config", config.toString(), "--stdout"},
                    new PrintStream(output, true, StandardCharsets.UTF_8), System.err);
            check(code == 0 && output.toString(StandardCharsets.UTF_8).equals(result.text), "Config and CLI share effects");
            check(AsciiCli.run(new String[] {input.toString(), "--dither", "invalid", "--stdout"}, System.out, new PrintStream(new ByteArrayOutputStream())) == 2,
                    "Bad effects fail before conversion");
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(config); }
        System.out.println("Effects checks passed: halftones, edges, palettes, escaping, parallel settings and CLI/config.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
