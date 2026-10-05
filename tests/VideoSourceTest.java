import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

public class VideoSourceTest {
    private static AsciiDashboard dashboard;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("galler video test ");
        Path movie = directory.resolve("test movie.mp4");
        try {
            Process encode = new ProcessBuilder(VideoSource.executable("ffmpeg"), "-v", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=6:duration=2",
                    "-c:v", "mpeg4", movie.toString()).inheritIO().start();
            check(encode.waitFor(30, TimeUnit.SECONDS) && encode.exitValue() == 0, "Generate MP4 fixture");
            VideoSource source = new VideoSource(movie.toFile());
            try {
                check(source.width == 160 && source.height == 90, "Probe dimensions");
                check(source.frameCount(6) == 12, "Duration and frame sampling");
                BufferedImage first = source.readFrame(0, 6, 1);
                BufferedImage later = source.readFrame(6, 6, 1);
                check(!java.util.Arrays.equals(first.getRGB(0, 0, 160, 90, null, 0, 160),
                        later.getRGB(0, 0, 160, 90, null, 0, 160)), "Frame content changes");
                BufferedImage down = source.readFrame(0, 6, 0.5);
                check(down.getWidth() == 80 && down.getHeight() == 45, "Downscale and backward seek");
                BufferedImage up = source.readFrame(11, 6, 2);
                check(up.getWidth() == 320 && up.getHeight() == 180, "Upscale and last frame");
                check(!ImageInspect.toAscii(up, 80).isBlank(), "Video frame converts to ASCII");
                FrameProcessing.VideoWindow window = new FrameProcessing.VideoWindow(source, 6, 1,
                        new FrameProcessing.Settings(80, true, 0.02, false, ImageInspect.OutputFormat.ORIGINAL));
                try {
                    for (int index : new int[] {0, 1, 8, 2, 11}) {
                        FrameProcessing.Frame processed = window.get(index);
                        check(processed.ascii.text.equals(ImageInspect.toAscii(source.readFrame(index, 6, 1), 80)),
                                "Prefetch and seeks preserve selected frame");
                        check(window.bufferedFrames() <= window.capacity && window.capacity <= 4, "Bounded video buffer");
                        check(processed.decodeNanos > 0 && processed.conversionNanos > 0, "Video timing metrics recorded");
                    }
                } finally { window.close(); }
                check(window.awaitStopped(5, TimeUnit.SECONDS), "Decoder workers shut down");
                check(window.bufferedFrames() == 0, "Close releases cached frames");
                try { window.get(0); throw new AssertionError("Closed window accepted work"); }
                catch (java.util.concurrent.CancellationException expected) { }
                try {
                    source.readFrame(12, 6, 1);
                    throw new AssertionError("Out-of-range frame accepted");
                } catch (IllegalArgumentException expected) { }
            } finally { source.close(); }
            try {
                source.readFrame(0, 6, 1);
                throw new AssertionError("Closed decoder accepted frame");
            } catch (java.io.IOException expected) { }

            SwingUtilities.invokeAndWait(() -> {
                dashboard = new AsciiDashboard();
                dashboard.loadImage(movie.toFile());
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((JButton) field("play")).isEnabled(), "MP4 playback enabled");
                check(((JLabel) field("timingInfo")).getText().contains("video frame"), "Status bar timing scope");
                check(((JLabel) field("memoryInfo")).getText().contains("Heap") && (Long) field("peakHeap") > 0,
                        "Live heap profiling displayed");
                ((JComboBox<?>) field("videoScale")).setSelectedItem("50%");
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((BufferedImage) field("source")).getWidth() == 80, "Dashboard downscales pixels");
                ((JComboBox<?>) field("videoScale")).setSelectedItem("200%");
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((BufferedImage) field("source")).getWidth() == 320, "Dashboard upscales pixels");
                ((JComboBox<?>) field("format")).setSelectedItem(ImageInspect.OutputFormat.HD);
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((ImageInspect.AsciiResult) field("generatedResult")).height == 45, "Video ASCII preset");
                ((JSpinner) field("frame")).setValue(6);
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> {
                check(((JLabel) field("frameInfo")).getText().contains("Frame 6 / 12"), "Seek updates selected frame");
                ((JButton) field("play")).doClick(0);
            });
            Thread.sleep(900);
            SwingUtilities.invokeAndWait(() -> {
                check((Integer) ((JSpinner) field("frame")).getValue() > 6, "Playback advances");
                if (((JButton) field("play")).getText().equals("Pause")) ((JButton) field("play")).doClick(0);
            });
            awaitPreview();
            SwingUtilities.invokeAndWait(() -> check(!(Boolean) field("playing") && !(Boolean) field("pendingVideoPlayback"),
                    "Pause stops playback after in-flight frame"));
            System.out.println("MP4 checks passed: decode, seek, scale, last frame, cleanup, dashboard, playback and pause.");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (dashboard != null) {
                    dashboard.dispose();
                    check(!((Timer) field("metricsTimer")).isRunning(), "Memory sampler stops on close");
                }
            });
            Files.deleteIfExists(movie);
            Files.deleteIfExists(directory);
        }
    }

    private static void awaitPreview() throws Exception {
        boolean[] ready = {false};
        for (int i = 0; i < 200 && !ready[0]; i++) {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> ready[0] = ((JButton) field("export")).isEnabled());
        }
        check(ready[0], "Video preview completed");
    }

    private static Object field(String name) {
        try {
            Field field = AsciiDashboard.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(dashboard);
        } catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
