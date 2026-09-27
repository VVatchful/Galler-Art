import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.*;

public class AsciiDashboardTest {
    private static AsciiDashboard dashboard;

    public static void main(String[] args) throws Exception {
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
            System.out.println("Dashboard checks passed: automatic format update, resizing, fit mode, manual zoom.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (dashboard != null) dashboard.dispose(); });
        }
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
