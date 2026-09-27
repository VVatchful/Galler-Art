import javax.swing.*;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.*;
public class DashboardSmoke {
    static AsciiDashboard dashboard;
    static Object field(String name) throws Exception {
        Field field = AsciiDashboard.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(dashboard);
    }
    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    dashboard = new AsciiDashboard();
                    if (((JButton) field("export")).isEnabled()) throw new AssertionError("Export initially enabled");
                    Field source = AsciiDashboard.class.getDeclaredField("source");
                    source.setAccessible(true);
                    source.set(dashboard, ImageIO.read(new File("Images/img.png")));
                    Method generate = AsciiDashboard.class.getDeclaredMethod("generate");
                    generate.setAccessible(true);
                    generate.invoke(dashboard);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            boolean[] ready = {false};
            for (int i = 0; i < 100 && !ready[0]; i++) {
                Thread.sleep(100);
                SwingUtilities.invokeAndWait(() -> {
                    try { ready[0] = ((JButton) field("export")).isEnabled(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            if (!ready[0]) throw new AssertionError("Preview did not finish");
            SwingUtilities.invokeAndWait(() -> {
                try {
                    String output = ((JTextArea) field("preview")).getText();
                    if (output.indexOf('\n') != 160) throw new AssertionError("Incorrect preview width");
                    ((JSpinner) field("columns")).setValue(240);
                    if (((JButton) field("export")).isEnabled()) throw new AssertionError("Stale export enabled");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("Dashboard smoke check passed: initialization, preview generation, stale export prevention.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (dashboard != null) dashboard.dispose(); });
        }
    }
}
