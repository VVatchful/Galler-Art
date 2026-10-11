import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Reproducibly captures the source and real Swing ASCII preview for README examples. */
public final class GenerateExamples {
    /**
     * Writes PNG screenshots under docs/images from the repository's sample image.
     * @param args unused; run from the project root
     * @throws Exception if source loading, rendering, or screenshot writing fails
     */
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(Path.of("Images/img.png").toFile());
        Files.createDirectories(Path.of("docs/images"));
        capture(source, "grayscale.png", "Grayscale ASCII", false, ImageInspect.DEFAULT_EFFECTS);
        capture(source, "color.png", "RGB ASCII", true, ImageInspect.DEFAULT_EFFECTS);
        capture(source, "dither.png", "Floyd-Steinberg / two-character palette", false,
                new ImageInspect.Effects(ImageInspect.Dither.FLOYD_STEINBERG, ImageInspect.Edges.NONE, " @"));
        capture(source, "edges.png", "Canny outlines / two-character palette", false,
                new ImageInspect.Effects(ImageInspect.Dither.NONE, ImageInspect.Edges.CANNY, " @"));
    }

    private static void capture(BufferedImage source, String filename, String title, boolean color,
                                ImageInspect.Effects effects) throws Exception {
        ImageInspect.AsciiResult result = ImageInspect.convert(source, 160, true, 0.02, false,
                ImageInspect.OutputFormat.ORIGINAL, effects);
        BufferedImage screenshot = new BufferedImage(1320, 560, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(null);
            panel.setBackground(new Color(24, 28, 35));
            panel.setSize(1320, 560);
            JLabel before = label("BEFORE / Images/img.png", 18);
            before.setBounds(16, 12, 638, 30);
            panel.add(before);
            JLabel after = label("AFTER / " + title, 18);
            after.setBounds(670, 12, 638, 30);
            panel.add(after);
            JLabel original = new JLabel(new ImageIcon(source));
            original.setBounds(16, 48, 638, 480);
            panel.add(original);
            AsciiDashboard.FittedPreview preview = new AsciiDashboard.FittedPreview();
            preview.setAscii(result.text);
            preview.setColors(color ? result : null);
            preview.setBounds(670, 48, 638, 480);
            panel.add(preview);
            JLabel caption = label("160 columns / original proportions / 2% contrast clipping / fit preview", 13);
            caption.setBounds(670, 528, 638, 25);
            panel.add(caption);
            Graphics2D graphics = screenshot.createGraphics();
            try { panel.printAll(graphics); } finally { graphics.dispose(); }
        });
        ImageIO.write(screenshot, "png", Path.of("docs/images", filename).toFile());
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(232, 237, 244));
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        return label;
    }
}
