import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameProcessingTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        CountDownLatch overlap = new CountDownLatch(2);
        List<Integer> results = FrameProcessing.ordered(6, 2, index -> {
            int running = active.incrementAndGet();
            maximum.accumulateAndGet(running, Math::max);
            try {
                if (index < 2) {
                    overlap.countDown();
                    check(overlap.await(3, TimeUnit.SECONDS), "Two workers overlap");
                }
                if (index % 2 == 0) Thread.sleep(15);
                return index;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            } finally { active.decrementAndGet(); }
        });
        check(results.equals(List.of(0, 1, 2, 3, 4, 5)), "Output remains ordered despite staggered completion");
        check(maximum.get() == 2, "Worker bound enforced");
        check(FrameProcessing.workerCount(Long.MAX_VALUE) == 1, "Large frames reduce parallelism");
        check(FrameProcessing.workerCount(1) <= 4, "Maximum four workers");
        try {
            FrameProcessing.ordered(4, 2, index -> { throw new IllegalStateException("conversion failed"); });
            throw new AssertionError("Worker failure was swallowed");
        } catch (java.util.concurrent.ExecutionException expected) {
            check(expected.getCause() instanceof IllegalStateException, "Worker failures propagate");
        }
        BufferedImage image = new BufferedImage(480, 320, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 320; y++) for (int x = 0; x < 480; x++) image.setRGB(x, y, (x * 719 + y * 131) & 0xffffff);
        FrameProcessing.Settings settings = new FrameProcessing.Settings(160, true, 0.02, false, ImageInspect.OutputFormat.ORIGINAL);
        String expected = settings.convert(image).text;
        long started = System.nanoTime();
        for (int i = 0; i < 12; i++) settings.convert(image);
        long serial = System.nanoTime() - started;
        started = System.nanoTime();
        List<ImageInspect.AsciiResult> frames = FrameProcessing.ordered(12, FrameProcessing.workerCount(settings.estimate(480, 320)),
                index -> settings.convert(image));
        long parallel = System.nanoTime() - started;
        for (ImageInspect.AsciiResult frame : frames) check(frame.text.equals(expected), "Parallel conversion matches serial output");
        System.out.printf(java.util.Locale.ROOT, "Synthetic 12-frame timings (not a benchmark guarantee): serial %.1f ms, parallel %.1f ms%n",
                serial / 1e6, parallel / 1e6);
        AsciiDashboard.FittedPreview panel = new AsciiDashboard.FittedPreview();
        panel.setAscii(expected);
        panel.setSize(600, 400);
        long[] paint = {0};
        panel.paintTiming = nanos -> paint[0] = nanos;
        java.awt.Graphics2D graphics = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB).createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        check(paint[0] > 0, "Paint metrics time actual rendering");
        System.out.println("Frame processing checks passed: overlap, ordering, limits, error propagation, conversion parity, paint timing.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
