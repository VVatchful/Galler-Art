# Galler-Art contributor guidance

## Purpose and established behavior
Build a Java application that converts still images, GIFs, and local MP4 videos
into ASCII art. Preserve the working grayscale/contrast mapping, optional averaged
RGB colors, whole-image fitting, resolution presets, and desktop playback.

## Project structure
- `src/ImageInspect.java`: image/GIF reading, frame composition, shared ASCII/RGB conversion and `ExportFormat` serializers.
- `src/VideoSource.java`: FFmpeg/ffprobe discovery, bounded-memory MP4 frame sampling and scaling.
- `src/AsciiDashboard.java`: Swing UI. Keep decoding/conversion off the event-dispatch thread.
- `src/AsciiCli.java` and `src/ConversionConfig.java`: batch CLI and reproducible settings.
- `tests/`: executable Java test classes; no test framework required.
- `README.md`: user-facing commands, supported formats, and limitations.

## Engineering rules
- Target JDK 11+. Keep the simple `javac` workflow; avoid adding build/runtime dependencies without a concrete need.
- Reuse the shared converter rather than creating separate grayscale or RGB algorithms for the CLI.
- Fit the complete source with padding; do not silently crop or distort it.
- Distinguish decoded pixel dimensions from ASCII columns/rows. Text cells use a 1:2 width/height ratio.
- Process movie frames incrementally; do not retain whole movies in memory.
- Invoke external programs with argument arrays, never shell-built command strings.
- Close decoders and temporary resources on success and failure.
- Keep CLI stdout for payloads and stderr for diagnostics. Return nonzero exit codes on failure.
- Validate configuration before conversion; document option precedence, units, and output naming.
- Preserve existing user changes and avoid editing generated `.class` files or IDE settings.
- Use an ignored fresh output directory for validation; do not commit media, downloaded tools, or build products.
- Add focused tests for conversion, parsing, output, or playback changes and update README examples.
- Keep work local unless the user requests publishing, deployment, or external communication.

## Validation
Compile `src/*.java` and `tests/*.java` to an ignored build directory using `javac`.
Run the relevant executable tests: `ImageInspectTest`, `AsciiColorTest`,
`GifFramesTest`, `AsciiDashboardTest`, `VideoSourceTest`, `AsciiCliTest`, and `ExportFormatsTest`.
Video tests need FFmpeg/ffprobe (`setup-ffmpeg.ps1` installs project-local binaries).
Run `git diff --check` and report checks actually performed and any limitations.

## Current boundaries
The dashboard supports local images, GIFs, and silent MP4 previews. Video frames are
sampled at a selected FPS; decoding may be slower than real time. TXT has no color;
HTML, ANSI, BBCode, Markdown with inline HTML, and SVG support color. SVG retains
text glyphs and a scalable viewBox. Markdown styles and BBCode tags depend on the
receiving renderer. ANSI must reset terminal styling. Do not describe frame exports as encoded video exports.
The CLI's documented configuration schema and examples are the source of truth for
supported JSON/YAML settings. Update this guidance when architecture changes.
