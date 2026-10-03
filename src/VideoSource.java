import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Bounded-memory, on-demand local video decoding. No whole-movie frame cache. */
public final class VideoSource implements AutoCloseable {
    public final File file;
    public final int width;
    public final int height;
    public final double duration;
    public final double sourceFps;
    private final Path scratch;
    private volatile Process process;
    private volatile boolean closed;

    public static String executable(String name) {
        String override = System.getenv(name.toUpperCase(Locale.ROOT) + "_PATH");
        if (override != null && !override.isBlank()) return override;
        Path local = Path.of("tools", "ffmpeg", "bin", name + ".exe");
        return Files.isRegularFile(local) ? local.toAbsolutePath().toString() : name;
    }

    public VideoSource(File file) throws Exception {
        if (!file.isFile()) throw new IOException("Video file not found: " + file);
        this.file = file.getAbsoluteFile();
        scratch = Files.createTempDirectory("galler-art-video-");
        try {
            Path metadata = scratch.resolve("metadata.txt");
            run(List.of(executable("ffprobe"), "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height,avg_frame_rate,duration:stream_side_data=rotation:format=duration",
                    "-of", "default=noprint_wrappers=1", this.file.toString()), metadata);
            int w = 0, h = 0, rotation = 0;
            double seconds = 0, fps = 0;
            for (String line : Files.readAllLines(metadata)) {
                String[] pair = line.split("=", 2);
                if (pair.length != 2 || pair[1].equals("N/A")) continue;
                switch (pair[0]) {
                    case "width": w = Integer.parseInt(pair[1]); break;
                    case "height": h = Integer.parseInt(pair[1]); break;
                    case "rotation": rotation = (int) Math.round(Double.parseDouble(pair[1])); break;
                    case "duration": if (seconds == 0) seconds = Double.parseDouble(pair[1]); break;
                    case "avg_frame_rate":
                        String[] rate = pair[1].split("/");
                        fps = Double.parseDouble(rate[0]) / (rate.length == 2 ? Double.parseDouble(rate[1]) : 1);
                        break;
                    default: break;
                }
            }
            if (w < 1 || h < 1 || !Double.isFinite(seconds) || seconds <= 0) {
                throw new IOException("The MP4 needs a readable video track and duration.");
            }
            boolean rotated = Math.abs(rotation % 180) == 90;
            width = rotated ? h : w;
            height = rotated ? w : h;
            duration = seconds;
            sourceFps = Double.isFinite(fps) && fps > 0 ? fps : 24;
        } catch (Exception exception) {
            close();
            throw exception;
        }
    }

    public int frameCount(double fps) {
        if (!Double.isFinite(fps) || fps < 1 || fps > 120) throw new IllegalArgumentException("FPS must be 1 to 120.");
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.ceil(duration * fps - 0.000001)));
    }

    public int[] scaledSize(double scale) {
        if (!Double.isFinite(scale) || scale < 0.125 || scale > 4) {
            throw new IllegalArgumentException("Video scale must be between 12.5% and 400%.");
        }
        long w = Math.max(1, Math.round(width * scale));
        long h = Math.max(1, Math.round(height * scale));
        if (w * h > 33_177_600 || w > 16384 || h > 16384) {
            throw new IllegalArgumentException("Scaled video is too large. Choose a smaller video scale (maximum 33 megapixels).");
        }
        return new int[] {(int) w, (int) h};
    }

    /** Samples the displayed frame at index / fps seconds, including variable-rate sources. */
    public BufferedImage readFrame(int index, double fps, double scale) throws Exception {
        if (index < 0 || index >= frameCount(fps)) throw new IllegalArgumentException("Frame is outside the movie.");
        int[] size = scaledSize(scale);
        Path frame = scratch.resolve("frame.png");
        List<String> command = new ArrayList<>(List.of(executable("ffmpeg"), "-hide_banner", "-loglevel", "error",
                "-nostdin", "-y", "-ss", String.format(Locale.ROOT, "%.9f", index / fps),
                "-i", file.toString(), "-map", "0:v:0", "-an", "-sn", "-frames:v", "1",
                "-vf", "scale=" + size[0] + ":" + size[1] + ":flags=lanczos,setsar=1",
                "-threads", "1", "-update", "1", frame.toString()));
        Files.deleteIfExists(frame);
        run(command, scratch.resolve("stdout.txt"));
        BufferedImage image = ImageIO.read(frame.toFile());
        if (image == null) throw new IOException("No video frame was decoded at this time.");
        return image;
    }

    private void run(List<String> command, Path output) throws Exception {
        if (closed) throw new IOException("Video decoder is closed.");
        Path errors = scratch.resolve("errors.txt");
        Process active;
        try {
            active = new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
        } catch (IOException exception) {
            throw new IOException("MP4 import needs FFmpeg and ffprobe. Run .\\setup-ffmpeg.ps1, or set FFMPEG_PATH and FFPROBE_PATH.", exception);
        }
        process = active;
        try {
            if (closed) throw new IOException("Video decoder is closed.");
            if (!active.waitFor(30, TimeUnit.SECONDS)) throw new IOException("Video decoding timed out after 30 seconds.");
            if (active.exitValue() != 0) {
                String message = Files.readString(errors, StandardCharsets.UTF_8);
                throw new IOException("Video decoder failed: " + message.substring(0, Math.min(1000, message.length())));
            }
        } finally {
            active.destroyForcibly();
            process = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        Process active = process;
        if (active != null) active.destroyForcibly();
        // Only remove this decoder's own known temporary files; no recursive deletion.
        for (String name : List.of("metadata.txt", "errors.txt", "stdout.txt", "frame.png")) {
            try { Files.deleteIfExists(scratch.resolve(name)); } catch (IOException ignored) { }
        }
        try { Files.deleteIfExists(scratch); } catch (IOException ignored) { }
    }
}
