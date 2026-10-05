import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ExportFormatsTest {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        int[] rgb = {0xff0000, 0x00ff00, 0x0000ff, 0};
        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, rgb[x]);
        ImageInspect.AsciiResult result = ImageInspect.convert(image, 4, false, 0, false, ImageInspect.OutputFormat.ORIGINAL);
        String ansi = result.toAnsi(true);
        check(ansi.contains("\u001b[38;2;255;0;0m") && ansi.contains("\u001b[38;2;0;255;0m")
                && ansi.contains("\u001b[38;2;0;0;255m"), "ANSI RGB channel values");
        check(ansi.replaceAll("\u001b\\[[0-9;]*m", "").equals(result.text), "ANSI preserves cells and spaces");
        check(ansi.endsWith("\u001b[0m\n"), "ANSI resets terminal styling");
        check(!result.toAnsi(false).contains("38;2"), "Grayscale ANSI has no RGB colors");
        String bbcode = result.toBbcode(true);
        check(bbcode.contains("[color=#ff0000]") && bbcode.contains("[color=#0000ff]"), "BBCode colors");
        check(bbcode.replaceAll("\\[/?(?:pre|font|color)[^\\]]*\\]", "").equals(result.text + "\n"), "BBCode spacing");
        check(!result.toBbcode(false).contains("[color="), "Grayscale BBCode");
        check(result.toMarkdown(false).equals("```text\n" + result.text + "```\n"), "Plain Markdown fencing");
        String markdown = result.toMarkdown(true);
        Element pre = parse(markdown);
        check(pre.getTagName().equals("pre") && pre.getTextContent().equals(result.text), "Styled Markdown preserves text");
        check(markdown.contains("color:#ff0000"), "Markdown inline RGB style");
        for (boolean color : new boolean[] {false, true}) {
            Element svg = parse(result.toSvg(color));
            check(svg.getAttribute("viewBox").equals("0 0 32 16"), "SVG grid uses 1:2 cells including padding");
            check(svg.getNamespaceURI().equals("http://www.w3.org/2000/svg"), "SVG namespace");
            NodeList glyphs = svg.getElementsByTagName("text");
            check(glyphs.getLength() == 3, "SVG keeps nonblank glyphs as vector text");
            for (int i = 0; i < glyphs.getLength(); i++) {
                Element glyph = (Element) glyphs.item(i);
                check(glyph.getAttribute("x").equals(Integer.toString(i * 8)), "SVG cell coordinates");
                check(glyph.getTextContent().charAt(0) == result.text.charAt(i), "SVG character mapping");
                check(glyph.getAttribute("fill").equals(color ? String.format("#%06x", rgb[i]) : "#e8edf4"), "SVG color");
            }
        }
        Path root = Files.createTempDirectory("galler-formats-");
        try {
            Path input = root.resolve("colors.png");
            ImageIO.write(image, "png", input.toFile());
            for (ImageInspect.ExportFormat format : ImageInspect.ExportFormat.values()) {
                String name = format.name().toLowerCase(java.util.Locale.ROOT);
                String colorFlag = format == ImageInspect.ExportFormat.TXT ? "--no-color" : "--color";
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int code = AsciiCli.run(new String[] {input.toString(), "--format", name, colorFlag, "--stdout"},
                        new PrintStream(bytes, true, StandardCharsets.UTF_8), System.err);
                check(code == 0 && bytes.size() > 0, "CLI stdout supports " + name);
                code = AsciiCli.run(new String[] {input.toString(), "--format", name, colorFlag,
                        "--output-dir", root.resolve("exports").toString()}, System.out, System.err);
                Path output = root.resolve("exports/colors.png/frame-000001." + format.extension);
                check(code == 0 && Files.readString(output).equals(bytes.toString(StandardCharsets.UTF_8)), "CLI file/stdout parity: " + name);
            }
            Path config = root.resolve("svg.yaml");
            Files.writeString(config, "format: svg\ncolor: true\n");
            check(AsciiCli.run(new String[] {input.toString(), "--config", config.toString(), "--stdout"},
                    new PrintStream(new ByteArrayOutputStream()), System.err) == 0, "Config supports SVG");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.delete(path);
            }
        }
        System.out.println("Export checks passed: ANSI resets/RGB, BBCode, Markdown, SVG XML/geometry, CLI/config and file extensions.");
    }

    private static Element parse(String markup) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(markup.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
