import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

/** Bounded parallel work with deterministic frame order. */
public final class FrameProcessing {
    public static int workerCount(long estimatedBytesPerFrame) {
        long budget = Math.min(128L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8);
        return (int) Math.max(1, Math.min(Math.min(4, Runtime.getRuntime().availableProcessors()),
                budget / Math.max(1, estimatedBytesPerFrame)));
    }

    private static ThreadPoolExecutor pool(int workers) {
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers), runnable -> {
                    Thread thread = new Thread(runnable, "ascii-frame-worker");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public static <T> List<T> ordered(int count, int workers, IntFunction<T> conversion) throws Exception {
        ThreadPoolExecutor executor = pool(workers);
        List<Future<T>> pending = new ArrayList<>();
        List<T> results = new ArrayList<>(count);
        try {
            for (int start = 0; start < count; start += workers) {
                pending.clear();
                for (int i = start; i < Math.min(count, start + workers); i++) {
                    final int index = i;
                    pending.add(executor.submit(() -> conversion.apply(index)));
                }
                for (Future<T> result : pending) results.add(result.get());
            }
            return List.copyOf(results);
        } finally {
            for (Future<T> result : pending) result.cancel(true);
            executor.shutdownNow();
        }
    }

    public static final class Settings {
        final int columns;
        final boolean contrast, invert;
        final double clip;
        final ImageInspect.OutputFormat format;
        public Settings(int columns, boolean contrast, double clip, boolean invert, ImageInspect.OutputFormat format) {
            this.columns = columns; this.contrast = contrast; this.clip = clip; this.invert = invert; this.format = format;
        }
        ImageInspect.AsciiResult convert(BufferedImage image) {
            int[] size = ImageInspect.outputSize(image, columns, format);
            if ((long) size[0] * size[1] > 4_000_000) throw new IllegalArgumentException("ASCII frame exceeds 4 million cells; reduce columns");
            return ImageInspect.convert(image, columns, contrast, clip, invert, format);
        }
        long estimate(int width, int height) {
            double ratio = format == ImageInspect.OutputFormat.ORIGINAL ? (double) height / width
                    : (double) format.pixelHeight / format.pixelWidth;
            long cells = (long) columns * Math.max(1, Math.round(columns * ratio / 2));
            // Source + decode buffers and converter arrays, not an exact heap measurement.
            return (long) width * height * 12 + cells * 48;
        }
    }

    public static final class Frame {
        final BufferedImage image;
        final ImageInspect.AsciiResult ascii;
        final long decodeNanos, conversionNanos;
        Frame(BufferedImage image, ImageInspect.AsciiResult ascii, long decode, long conversion) {
            this.image = image; this.ascii = ascii; decodeNanos = decode; conversionNanos = conversion;
        }
    }

    /** Current frame plus a bounded look-ahead window; each job owns its decoder files/process. */
    public static final class VideoWindow implements AutoCloseable {
        private final VideoSource source;
        private final double fps, scale;
        private final Settings settings;
        private final ThreadPoolExecutor executor;
        private final Map<Integer, Future<Frame>> frames = new LinkedHashMap<>();
        public final int capacity;
        private final int frameCount;
        private boolean closed;

        public VideoWindow(VideoSource source, double fps, double scale, Settings settings) {
            this(source, fps, scale, settings, source.frameCount(fps));
        }

        public VideoWindow(VideoSource source, double fps, double scale, Settings settings, int limit) {
            this.source = source; this.fps = fps; this.scale = scale; this.settings = settings;
            frameCount = Math.min(source.frameCount(fps), limit);
            if (frameCount < 1) throw new IllegalArgumentException("Video window needs at least one frame");
            int[] size = source.scaledSize(scale);
            capacity = workerCount(settings.estimate(size[0], size[1]));
            executor = pool(capacity);
        }

        public Frame get(int index) throws Exception {
            Future<Frame> current;
            synchronized (this) {
                if (closed) throw new CancellationException("Video pipeline closed");
                int count = frameCount;
                if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid frame index");
                Iterator<Map.Entry<Integer, Future<Frame>>> iterator = frames.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, Future<Frame>> entry = iterator.next();
                    if (entry.getKey() < index || entry.getKey() >= (long) index + capacity) {
                        entry.getValue().cancel(true); iterator.remove();
                    }
                }
                executor.purge();
                for (int i = index; i < Math.min((long) count, (long) index + capacity); i++) {
                    final int next = i;
                    if (!frames.containsKey(i)) frames.put(i, executor.submit(() -> {
                        try (VideoSource decoder = source.fork()) {
                            long start = System.nanoTime();
                            BufferedImage image = decoder.readFrame(next, fps, scale);
                            long decoded = System.nanoTime();
                            ImageInspect.AsciiResult ascii = settings.convert(image);
                            return new Frame(image, ascii, decoded - start, System.nanoTime() - decoded);
                        }
                    }));
                }
                current = frames.get(index);
            }
            return current.get();
        }

        public synchronized int bufferedFrames() { return frames.size(); }
        boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        public synchronized void close() {
            closed = true;
            for (Future<Frame> future : frames.values()) future.cancel(true);
            frames.clear();
            executor.shutdownNow();
        }
    }
}
