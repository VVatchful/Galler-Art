import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.*;

public class AsciiDashboardTest {
    private static AsciiDashboard dashboard;

    public static void main(String[] args) throws Exception {
        Path gif = Files.createTempFile("galler-art-dashboard-", ".gif");
        try {
            SwingUtilities.invokeAndWait(() -> {
                AsciiDashboard.FittedPreview panel = new AsciiDashboard.FittedPreview();
                for (int[] grid : new int[][] {{480, 135}, {135, 120}, {160, 333}}) {
                    panel.setAscii(("@".repeat(grid[0]) + "\n").repeat(grid[1]));
                    for (int[] viewport : new int[][] {{800, 600}, {300, 200}, {1200, 900}}) {
                        panel.setSize(viewport[0], viewport[1]);
                        Rectangle bounds = panel.fittedBounds();
                        check(bounds.x >= 12 && bounds.y >= 12, "Centered margins");
                        check(bounds.getMaxX() <= viewport[0] - 12 && bounds.getMaxY() <= viewport[1] - 12,
                                "Whole preview fits the viewport");
                        double expectedHeight = bounds.width * (2.0 * grid[1] / grid[0]);
                        check(Math.abs(bounds.height - expectedHeight) < 6, "Preserved cell proportions");
                    }
                }
                dashboard = new AsciiDashboard();
                setField("source", new BufferedImage(2560, 1040, BufferedImage.TYPE_INT_RGB));
                JComboBox<?> formats = (JComboBox<?>) field("format");
                formats.setSelectedItem(ImageInspect.OutputFormat.UHD);
            });
            boolean[] ready = {false};
            for (int attempt = 0; attempt < 100 && !ready[0]; attempt++) {
                Thread.sleep(100);
                SwingUtilities.invokeAndWait(() -> ready[0] = ((JButton) field("export")).isEnabled());
            }
            check(ready[0], "Selecting a format automatically generates output");
            SwingUtilities.invokeAndWait(() -> {
                String text = ((JTextArea) field("preview")).getText();
                check(text.indexOf('\n') == 480 && text.lines().count() == 135, "Selected 4K text dimensions");
                check(((JCheckBox) field("fitPreview")).isSelected(), "Fit enabled by default");
                check(!((JSpinner) field("fontSize")).isEnabled(), "Manual zoom disabled in fit mode");
                ((JCheckBox) field("fitPreview")).doClick();
                check(((JSpinner) field("fontSize")).isEnabled(), "Manual zoom available");
                check(text.equals(((JTextArea) field("preview")).getText()), "View mode preserves output");
            });
            GifFramesTest.write(gif,
                    new GifFramesTest.Patch(6, 4, 1, 0, 0, "doNotDispose", 3, false),
                    new GifFramesTest.Patch(2, 2, 2, 2, 0, "none", 7, false));
            SwingUtilities.invokeAndWait(() -> dashboard.loadImage(gif.toFile()));
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((java.util.List<?>) field("generatedFrames")).size() == 2, "Import converts every GIF frame");
                JSpinner frame = (JSpinner) field("frame");
                check(frame.isEnabled(), "Frame navigation enabled for animation");
                String first = ((JTextArea) field("preview")).getText();
                frame.setValue(2);
                check(!first.equals(((JTextArea) field("preview")).getText()), "Selecting a frame updates ASCII");
                check(((BufferedImage) field("source")).getRGB(2, 0) == 0xff00ff00, "Original uses selected composed frame");
                check(((JLabel) field("frameInfo")).getText().contains("70 ms"), "Selected frame delay displayed");
                ((JCheckBox) field("color")).doClick();
                ((JCheckBox) field("invert")).doClick();
                frame.setValue(1);
                check(!((JButton) field("export")).isEnabled(), "Navigating cannot export stale settings");
                check(((JTextArea) field("preview")).getText().isEmpty(), "Stale ASCII cleared");
                ((JButton) field("convert")).doClick();
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((java.util.List<?>) field("generatedFrames")).size() == 2, "Options regenerate every frame");
                ((JSpinner) field("frame")).setValue(2);
            });
            javax.imageio.ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", gif.toFile());
            SwingUtilities.invokeAndWait(() -> dashboard.loadImage(gif.toFile()));
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((JSpinner) field("frame")).getValue().equals(1), "New image resets frame selection");
                check(!((JSpinner) field("frame")).isEnabled(), "Still image disables frame navigation");
                check(((java.util.List<?>) field("generatedFrames")).size() == 1, "Old animation results removed");
            });
            System.out.println("Dashboard checks passed: formats, fit, zoom, GIF import, frame navigation, regeneration, still-image reset.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (dashboard != null) dashboard.dispose(); });
            Files.deleteIfExists(gif);
        }
    }

    private static void awaitPreview() throws Exception {
        boolean[] ready = {false};
        for (int attempt = 0; attempt < 100 && !ready[0]; attempt++) {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> ready[0] = ((JButton) field("export")).isEnabled());
        }
        check(ready[0], "Background import and conversion completed");
    }

    private static Object field(String name) {
        try {
            Field field = AsciiDashboard.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(dashboard);
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }

    private static void setField(String name, Object value) {
        try {
            Field field = AsciiDashboard.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(dashboard, value);
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
