import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Scriptable entry point; the legacy ImageInspect launcher remains compatible. */
public final class AsciiCli {
    private static final Set<String> KEYS = Set.of("preset", "columns", "autoContrast", "clip", "invert", "color",
            "fps", "scale", "format", "outputDir", "recursive", "overwrite", "maxFrames");
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "mp4");

    public static void main(String[] args) {
        System.exit(run(args, new PrintStream(System.out, true, StandardCharsets.UTF_8), System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            if (Arrays.asList(args).contains("--help")) { out.print(help()); return 0; }
            options = parse(args);
        } catch (Exception exception) {
            err.println("Settings error: " + exception.getMessage());
            err.println("Use --help for usage.");
            return 2;
        }
        try {
            Path input = options.input.toAbsolutePath().normalize();
            List<Path> inputs;
            if (options.batch) {
                if (!Files.isDirectory(input)) throw new IllegalArgumentException("Batch input must be a directory: " + input);
                try (Stream<Path> paths = Files.walk(input, options.recursive ? Integer.MAX_VALUE : 1)) {
                    inputs = paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && supported(p))
                            .sorted().collect(Collectors.toList());
                }
            } else {
                if (!Files.isRegularFile(input) || !supported(input))
                    throw new IllegalArgumentException("Input must be a PNG, JPEG, BMP, GIF, or MP4 file.");
                inputs = List.of(input);
            }
            if (inputs.isEmpty()) throw new IllegalArgumentException("No supported media files found.");
            Sink sink = new Sink(options, out);
            int failed = 0;
            for (Path file : inputs) {
                String relative = (options.batch ? input.relativize(file) : file.getFileName()).toString();
                try {
                    convert(file, relative, options, sink);
                    err.println("Converted: " + relative);
                } catch (Exception exception) {
                    failed++;
                    err.println("Failed: " + relative + ": " + exception.getMessage());
                    if (out.checkError()) return 1;
                }
            }
            err.println("Completed: " + (inputs.size() - failed) + " succeeded, " + failed + " failed.");
            return failed == 0 ? 0 : 1;
        } catch (Exception exception) {
            err.println("Conversion error: " + exception.getMessage());
            return 1;
        }
    }

    private static void convert(Path path, String name, Options o, Sink sink) throws Exception {
        if (extension(path).equals("mp4")) {
            try (VideoSource video = new VideoSource(path.toFile())) {
                double fps = o.fps == 0 ? Math.max(1, Math.min(120, video.sourceFps)) : o.fps;
                int count = video.frameCount(fps);
                if (o.maxFrames > 0) count = Math.min(count, o.maxFrames);
                try (FrameProcessing.VideoWindow window = new FrameProcessing.VideoWindow(video, fps, o.scale,
                        new FrameProcessing.Settings(o.columns, o.autoContrast, o.clip, o.invert, o.preset), count)) {
                    for (int i = 0; i < count; i++) {
                        sink.write(name, i + 1, Math.round(i * 1000 / fps), Math.round(1000 / fps), window.get(i).ascii);
                    }
                }
            }
        } else {
            List<ImageInspect.ImageFrame> frames = ImageInspect.readFrames(path.toFile());
            int count = o.maxFrames > 0 ? Math.min(frames.size(), o.maxFrames) : frames.size();
            long time = 0;
            BufferedImage first = frames.get(0).image;
            FrameProcessing.Settings settings = new FrameProcessing.Settings(o.columns, o.autoContrast, o.clip, o.invert, o.preset);
            int workers = FrameProcessing.workerCount(settings.estimate(first.getWidth(), first.getHeight()));
            for (int start = 0; start < count; start += workers) {
                final int offset = start;
                List<ImageInspect.AsciiResult> results = FrameProcessing.ordered(Math.min(workers, count - start), workers,
                        index -> render(frames.get(offset + index).image, o));
                for (int j = 0; j < results.size(); j++) {
                    ImageInspect.ImageFrame frame = frames.get(start + j);
                    sink.write(name, start + j + 1, time, frame.delayMillis, results.get(j));
                    time += frame.delayMillis;
                }
            }
        }
    }

    private static ImageInspect.AsciiResult render(BufferedImage image, Options o) {
        int[] size = ImageInspect.outputSize(image, o.columns, o.preset);
        if ((long) size[0] * size[1] > 4_000_000) {
            throw new IllegalArgumentException("ASCII frame exceeds 4 million cells; reduce columns or choose a format preset");
        }
        return ImageInspect.convert(image, o.columns, o.autoContrast, o.clip, o.invert, o.preset);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    private static boolean supported(Path path) { return EXTENSIONS.contains(extension(path)); }

    private static Options parse(String[] args) throws Exception {
        Map<String, String> flags = new LinkedHashMap<>();
        Path config = null, input = null;
        boolean batch = false, stdout = false;
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("--stdout")) { stdout = true; continue; }
            if (flag.equals("--batch")) {
                if (input != null || ++i == args.length) throw new IllegalArgumentException("--batch requires one input directory");
                batch = true; input = Path.of(args[i]); continue;
            }
            if (!flag.startsWith("--")) {
                if (input != null) throw new IllegalArgumentException("Supply only one input file or --batch directory");
                input = Path.of(flag); continue;
            }
            if (flag.equals("--config")) {
                if (config != null || ++i == args.length) throw new IllegalArgumentException("Supply one --config file");
                config = Path.of(args[i]).toAbsolutePath().normalize(); continue;
            }
            String key = flag.substring(2);
            boolean negative = key.startsWith("no-");
            if (negative) key = key.substring(3);
            switch (key) {
                case "contrast": key = "autoContrast"; break;
                case "output-dir": key = "outputDir"; break;
                case "max-frames": key = "maxFrames"; break;
                default: break;
            }
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown option: " + flag);
            boolean toggle = Set.of("autoContrast", "invert", "color", "recursive", "overwrite").contains(key);
            if (negative && !toggle) throw new IllegalArgumentException("Not a boolean option: " + flag);
            if (toggle) flags.put(key, Boolean.toString(!negative));
            else {
                if (++i == args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for " + flag);
                flags.put(key, args[i]);
            }
        }
        if (input == null) throw new IllegalArgumentException("Supply an input file or --batch directory");
        Map<String, String> settings = config == null ? new LinkedHashMap<>() : ConversionConfig.read(config);
        for (String key : settings.keySet()) if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown config key: " + key);
        if (settings.containsKey("outputDir")) {
            settings.put("outputDir", config.getParent().resolve(settings.get("outputDir")).normalize().toString());
        }
        settings.putAll(flags);
        Options o = new Options();
        o.input = input; o.batch = batch; o.stdout = stdout;
        String preset = settings.getOrDefault("preset", "original").toUpperCase(Locale.ROOT).replace('-', '_');
        switch (preset) {
            case "720P": preset = "HD"; break;
            case "1080P": preset = "FULL_HD"; break;
            case "4K": preset = "UHD"; break;
            default: break;
        }
        try { o.preset = ImageInspect.OutputFormat.valueOf(preset); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown preset: " + preset); }
        o.columns = integer(settings, "columns", o.preset.columns, 1, 2000);
        o.autoContrast = bool(settings, "autoContrast", true);
        o.invert = bool(settings, "invert", false);
        o.color = bool(settings, "color", false);
        o.recursive = bool(settings, "recursive", false);
        o.overwrite = bool(settings, "overwrite", false);
        o.clip = number(settings, "clip", 0.02, 0, 0.499999);
        o.scale = number(settings, "scale", 1, 0.125, 4);
        o.fps = number(settings, "fps", 0, 0, 120);
        if (o.fps > 0 && o.fps < 1) throw new IllegalArgumentException("fps must be 0 (source rate) or 1 to 120");
        o.maxFrames = integer(settings, "maxFrames", 0, 0, Integer.MAX_VALUE);
        o.format = settings.getOrDefault("format", "txt").toLowerCase(Locale.ROOT);
        if (o.format.equals("md")) o.format = "markdown";
        if (o.format.equals("ans")) o.format = "ansi";
        if (!Set.of("txt", "html", "jsonl", "ansi", "bbcode", "markdown", "svg").contains(o.format))
            throw new IllegalArgumentException("format must be txt, html, jsonl, ansi, bbcode, markdown, or svg");
        if (o.color && o.format.equals("txt")) throw new IllegalArgumentException("TXT is plain text; select another format for color");
        if (o.stdout && Set.of("html", "svg").contains(o.format)
                && (o.batch || (Set.of("gif", "mp4").contains(extension(input)) && o.maxFrames != 1)))
            throw new IllegalArgumentException("HTML/SVG stdout requires one input image, or --max-frames 1 for GIF/MP4");
        o.output = Path.of(settings.getOrDefault("outputDir", "ascii")).toAbsolutePath().normalize();
        return o;
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.getOrDefault(key, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }
    private static double number(Map<String, String> values, String key, double fallback, double min, double max) {
        double value = Double.parseDouble(values.getOrDefault(key, Double.toString(fallback)));
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return value;
    }
    private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
        double value = number(values, key, fallback, min, max);
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + " must be an integer");
        return (int) value;
    }

    private static final class Options {
        Path input, output;
        boolean batch, stdout, recursive, overwrite, autoContrast, invert, color;
        int columns, maxFrames;
        double clip, scale, fps;
        String format;
        ImageInspect.OutputFormat preset;
    }

    private static final class Sink {
        final Options options;
        final PrintStream out;
        boolean emitted;
        Sink(Options options, PrintStream out) { this.options = options; this.out = out; }
        void write(String source, int frame, long time, long delay, ImageInspect.AsciiResult result) throws Exception {
            ImageInspect.ExportFormat exportFormat = options.format.equals("jsonl") ? null
                    : ImageInspect.ExportFormat.valueOf(options.format.toUpperCase(Locale.ROOT));
            String payload = exportFormat == null ? jsonFrame(source, frame, time, delay, result, options.color)
                    : result.export(exportFormat, options.color);
            if (options.stdout) {
                if (emitted && !options.format.equals("jsonl")) out.print('\f');
                out.print(payload); out.flush();
                if (out.checkError()) throw new java.io.IOException("Stdout pipe closed");
                emitted = true;
            } else {
                Path directory = options.output.resolve(source).normalize();
                if (!directory.startsWith(options.output)) throw new IllegalArgumentException("Output path escapes output directory");
                Files.createDirectories(directory);
                Path target = directory.resolve(String.format(Locale.ROOT, "frame-%06d.%s", frame,
                        exportFormat == null ? "jsonl" : exportFormat.extension));
                if (options.overwrite) Files.writeString(target, payload, StandardCharsets.UTF_8);
                else Files.writeString(target, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
        }
    }

    private static String jsonFrame(String source, int frame, long time, long delay, ImageInspect.AsciiResult result, boolean color) {
        StringBuilder json = new StringBuilder("{\"source\":").append(quote(source.replace('\\', '/')))
                .append(",\"frame\":").append(frame).append(",\"timeMillis\":").append(time)
                .append(",\"delayMillis\":").append(delay).append(",\"columns\":").append(result.width)
                .append(",\"rows\":").append(result.height).append(",\"text\":").append(quote(result.text));
        if (color) {
            json.append(",\"rgb\":[");
            for (int y = 0; y < result.height; y++) for (int x = 0; x < result.width; x++) {
                if (x != 0 || y != 0) json.append(',');
                json.append(result.rgbAt(x, y));
            }
            json.append(']');
        }
        return json.append("}\n").toString();
    }

    private static String quote(String text) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') escaped.append('\\').append(c);
            else if (c < 32) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.append('"').toString();
    }

    private static String help() {
        return "Usage: AsciiCli <file> [options] | AsciiCli --batch <directory> [options]\n"
                + "  --config <json|yaml>       Flat conversion settings; CLI options override config\n"
                + "  --preset <name>           original, sd, hd/720p, full-hd/1080p, qhd, uhd/4k, square, portrait, vertical\n"
                + "  --columns <1..2000>       Override preset character width\n"
                + "  --[no-]contrast --clip <0..0.499999> --[no-]invert --[no-]color\n"
                + "  --fps <0|1..120>          MP4 sampling rate; 0 uses source average FPS\n"
                + "  --scale <0.125..4>        MP4 pixel scaling factor\n"
                + "  --max-frames <count>      Per-input limit; 0 converts all frames\n"
                + "  --format <txt|html|jsonl|ansi|bbcode|markdown|svg> --output-dir <directory> (default: ascii)\n"
                + "  --[no-]recursive --[no-]overwrite\n"
                + "  --stdout                 No files; frames separated by form feed, or JSON Lines; HTML/SVG single frame only\n"
                + "Exit codes: 0 success, 1 conversion failure, 2 invalid settings. Diagnostics use stderr.\n";
    }
}
