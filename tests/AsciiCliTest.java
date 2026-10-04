import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public class AsciiCliTest {
    private static final class Result {
        int code;
        String stdout, stderr;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ascii-cli-tests-");
        try {
            Path input = Files.createDirectory(root.resolve("input"));
            Path nested = Files.createDirectory(input.resolve("nested"));
            BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 12; x++) image.setRGB(x, y, 0xff8040);
            ImageIO.write(image, "png", input.resolve("same.png").toFile());
            ImageIO.write(image, "jpg", input.resolve("same.jpg").toFile());
            ImageIO.write(image, "png", nested.resolve("same.png").toFile());
            Files.writeString(input.resolve("ignored.txt"), "Not media");
            Path config = root.resolve("options.json");
            Files.writeString(config, "{\"preset\":\"hd\",\"columns\":30,\"outputDir\":\"results\",\"recursive\":true}");
            Result result = run("--batch", input.toString(), "--config", config.toString(), "--columns", "20");
            check(result.code == 0 && result.stdout.isEmpty(), "Batch diagnostics stay off stdout");
            Path first = root.resolve("results/same.png/frame-000001.txt");
            check(Files.readString(first).indexOf('\n') == 20, "CLI overrides config columns");
            check(Files.exists(root.resolve("results/same.jpg/frame-000001.txt")), "Extensions avoid basename collision");
            check(Files.exists(root.resolve("results/nested/same.png/frame-000001.txt")), "Recursive relative hierarchy");
            check(run("--batch", input.toString(), "--config", config.toString()).code == 1, "Existing output is protected");
            check(run("--batch", input.toString(), "--config", config.toString(), "--overwrite").code == 0, "Explicit overwrite");

            Path yaml = root.resolve("options.yaml");
            Files.writeString(yaml, "# config\npreset: 'hd'\ncolumns: 30\noutputDir: \"results\" # comment\nrecursive: true\n");
            check(ConversionConfig.read(config).equals(ConversionConfig.read(yaml)), "Equivalent JSON/YAML");
            Result json = run(input.resolve("same.png").toString(), "--config", config.toString(), "--stdout", "--format", "jsonl", "--color");
            Result yamlResult = run(input.resolve("same.png").toString(), "--config", yaml.toString(), "--stdout", "--format", "jsonl", "--color");
            check(json.code == 0 && json.stdout.equals(yamlResult.stdout), "Config output parity");
            check(json.stdout.lines().count() == 1 && json.stdout.contains("\"rgb\":[") && json.stdout.contains("\"frame\":1"), "JSONL metadata and color");
            Result plain = run(input.resolve("same.png").toString(), "--stdout", "--columns", "12");
            check(plain.stdout.equals(ImageInspect.toAscii(image, 12)), "Stdout contains exact ASCII only");
            result = run("--batch", input.toString(), "--stdout");
            check(result.code == 0 && result.stdout.chars().filter(c -> c == '\f').count() == 1, "Two top-level files separated by form feed");
            check(run("--batch", input.toString(), "--stdout", "--format", "html").code == 2, "Reject HTML stdout");
            check(run(input.resolve("same.png").toString(), "--stdout", "--clip", "NaN").code == 2, "Reject nonfinite settings");
            check(run(input.resolve("same.png").toString(), "--stdout", "--fps", "0.5").code == 2, "Reject invalid FPS");
            check(run(input.resolve("same.png").toString(), "--stdout", "--columns", "1.5").code == 2, "Reject fractional columns");
            for (String invalid : List.of("{\"columns\":10,\"columns\":20}", "{\"columns\":10,}",
                    "{\"columns\":[10]}", "{\"typo\":true}", "{\"columns\":null}")) {
                Files.writeString(config, invalid);
                check(run(input.resolve("same.png").toString(), "--config", config.toString(), "--stdout").code == 2, "Reject invalid config: " + invalid);
            }
            Files.writeString(yaml, "columns: 10\ncolumns: 20\n");
            check(run(input.resolve("same.png").toString(), "--config", yaml.toString(), "--stdout").code == 2, "Reject duplicate YAML keys");
            Files.writeString(input.resolve("broken.png"), "not an image");
            result = run("--batch", input.toString(), "--stdout", "--format", "jsonl");
            check(result.code == 1 && result.stdout.lines().count() == 2 && result.stderr.contains("broken.png"), "Continue after per-file failure");

            Path gif = root.resolve("animated.gif");
            GifFramesTest.write(gif,
                    new GifFramesTest.Patch(6, 4, 1, 0, 0, "none", 3, false),
                    new GifFramesTest.Patch(2, 2, 2, 2, 0, "none", 7, false));
            result = run(gif.toString(), "--stdout", "--format", "jsonl");
            check(result.code == 0 && result.stdout.lines().count() == 2 && result.stdout.contains("\"timeMillis\":30"), "GIF frame timing");
            check(run(gif.toString(), "--stdout", "--format", "jsonl", "--max-frames", "1").stdout.lines().count() == 1, "Frame limit");

            Path movie = input.resolve("sample video.mp4");
            Process encoder = new ProcessBuilder(VideoSource.executable("ffmpeg"), "-v", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=80x48:rate=4:duration=1", "-c:v", "mpeg4", movie.toString()).inheritIO().start();
            try {
                check(encoder.waitFor(30, TimeUnit.SECONDS) && encoder.exitValue() == 0, "Generate MP4");
            } finally { encoder.destroyForcibly(); }
            result = run(movie.toString(), "--stdout", "--format", "jsonl", "--fps", "2", "--scale", "0.5", "--columns", "80");
            check(result.code == 0 && result.stdout.lines().count() == 2 && result.stdout.contains("\"timeMillis\":500")
                    && result.stdout.contains("\"columns\":40"), "MP4 sampling and scale");
            Files.delete(input.resolve("broken.png"));
            result = run("--batch", input.toString(), "--stdout", "--format", "jsonl", "--max-frames", "1");
            check(result.code == 0 && result.stdout.lines().count() == 3, "Mixed image/video batch");
            System.out.println("CLI checks passed: config validation/parity, precedence, batch paths, overwrite, stdout, failures, GIF and MP4.");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(path);
            }
        }
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        Result result = new Result();
        result.code = AsciiCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        result.stdout = out.toString(StandardCharsets.UTF_8);
        result.stderr = err.toString(StandardCharsets.UTF_8);
        return result;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
