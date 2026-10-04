# Galler-Art

A Java image-to-ASCII converter with a small desktop dashboard. Requires JDK 11 or newer.

## Dashboard

From PowerShell in the project folder:

```powershell
.\run-dashboard.ps1
```

Or run `AsciiDashboard.main()` in IntelliJ. You can also compile and launch manually:

```powershell
javac -d out/dashboard src/ImageInspect.java src/VideoSource.java src/AsciiDashboard.java
java -cp out/dashboard AsciiDashboard
```

Choose an image to load the original and generate an ASCII preview. Change the width,
contrast clipping percentage, or brightness inversion, then select **Generate preview**.
Selecting a resolution format automatically regenerates the preview.
The clipping percentage applies to each end of the brightness range; larger values
increase contrast but lose shadow and highlight detail. Disable **Enhance contrast**
to use the original grayscale range. **Fit preview to window** is enabled by default:
the entire output fits in the panel and scales as you resize the window. Disable it
to inspect characters using the preview font-size control and scrollbars. Neither
view mode changes the exported text or conversion resolution.

After selecting an image, choose a format: original proportions, SD, HD (720p),
Full HD (1080p), QHD, 4K, square, portrait (4:5), or vertical (9:16).
Resolution names describe the target proportions and detail preset, not a pixel
image export: TXT files contain columns and rows. Presets use approximately one
character per 8 horizontal pixels and one row per 16 vertical pixels, rounded to
whole cells. For example, HD produces 160 columns by 45 rows; Full HD produces
240 columns by 68 rows. You can adjust the character width after choosing a preset.
The dashboard shows the resulting text dimensions before generating.

The entire image is centered inside the selected format with blank padding as
needed. A 2560 x 1040 source fits inside HD with padding above and below, without
cropping or stretching. Padding does not affect the contrast calculation.
Choose original proportions to avoid format padding.

**Export TXT** saves the generated preview to `ascii/<image-name>.txt`, replacing
an existing file with the same name. Text previews do not wrap. The original preview
always fits the image.

Enable **Use original RGB colors** to color the ASCII characters in either preview
mode. Each cell uses the average red, green, and blue values of its source pixels;
transparency is composited onto black. Contrast and inversion still control character
density, while the RGB colors remain those of the image. The color toggle updates
the preview immediately without another conversion.

**Export HTML** saves `ascii/<image-name>.html` with the selected color mode. Open
it in a browser to view the colored ASCII. **Export TXT** still saves plain text;
TXT files cannot store character colors. Existing exports with the same name are replaced.

The dashboard's **Export format** selector also offers ANSI, BBCode, Markdown, and
SVG. Select a format, then use the button below it (for example, **Export SVG**).
**Use original RGB colors** controls color in each format. The separate **Export TXT**
button remains available for plain text. GIFs and videos export the selected frame.

| Format | CLI/config value | Extension | Output |
| --- | --- | --- | --- |
| Plain text | `txt` | `.txt` | ASCII characters and whitespace |
| HTML | `html` | `.html` | Standalone browser document with RGB spans |
| ANSI | `ansi` (alias `ans`) | `.ans` | Terminal SGR sequences with 24-bit RGB foregrounds, black background, and a reset after every row |
| BBCode | `bbcode` | `.bbcode` | `[pre][font=monospace]` wrapping and optional `[color=#RRGGBB]` tags |
| Markdown | `markdown` (alias `md`) | `.md` | Fenced plain text without color; inline-styled HTML `<pre>`/`<span>` with color |
| SVG | `svg` | `.svg` | Standalone vector text on black, with a scalable viewBox and explicit glyph coordinates |
| JSON Lines (CLI only) | `jsonl` | `.jsonl` | Frame metadata, ASCII text, and optional packed RGB values |

ANSI needs a terminal that interprets
[RGB escape sequences](https://learn.microsoft.com/en-us/windows/console/console-virtual-terminal-sequences).
Colored Markdown uses [raw HTML blocks](https://spec.commonmark.org/0.31.2/#html-blocks);
the receiving renderer must permit inline styles. Renderers that sanitize HTML/CSS
may remove color. BBCode dialects differ: the destination must support `pre`,
`font`, and nested `color` tags; some forums disable formatting inside preformatted
blocks. BBCode cannot reliably specify a black background across forums.

SVG retains editable text rather than embedding a bitmap. Its grid uses 8 by 16
units per character; whitespace padding is preserved in the canvas. It scales
without changing the grid, although the exact glyph shape depends on the viewer's
monospace font. Export sizes grow with the number of nonblank characters.

PNG, JPEG, BMP, and all frames of GIF images are supported. GIF frames are composed
at the full canvas size, respecting offsets, transparency, local palettes, and
disposal (keep, restore background, or restore previous). Transparent areas use the
same black background as still-image ASCII conversion.

Importing a GIF generates ASCII for every frame. Use **Frame** to inspect matching
original and ASCII frames; the label shows the frame count and original delay in
milliseconds. Changing conversion settings regenerates all frames when you select
**Generate preview**. RGB colors and both preview modes work with every frame.
For multi-frame images, TXT and HTML export the selected frame to
`ascii/<image-name>-frame-0001.txt` or `.html`, using its one-based frame number.
Frames and ASCII results are held in memory, so large GIFs require more memory.
Use **Play** / **Pause** to animate the original and ASCII previews together.
**Loop** is enabled by default; turn it off to stop on the final frame. Play resumes
from the selected frame, or restarts from the beginning when the final frame is
selected. Playback uses each frame's delay, with a 100 ms fallback for zero delays.
Selecting a frame manually, changing conversion settings, importing, or exporting
pauses playback. Animated export is not implemented yet.

## MP4 movies

Use **Choose image or MP4...** to open a local downloaded MP4. Video decoding uses
FFmpeg and ffprobe, with Windows builds linked from the
[FFmpeg download page](https://ffmpeg.org/download.html). Install them locally once:

```powershell
.\setup-ffmpeg.ps1
.\run-dashboard.ps1
```

The setup script downloads the Gyan essentials build into the project and puts the
executables in `tools/ffmpeg/bin`; it does not modify the system PATH. Alternatively,
install both tools on PATH or set `FFMPEG_PATH` and `FFPROBE_PATH` to their executable
paths. Downloaded tools and build output are excluded from Git.

MP4 import displays source dimensions, duration, and average FPS. Each requested
frame is decoded and converted in a background worker. Only the current decoded
frame and ASCII result are retained, so memory usage does not grow with movie length.
The decoder uses temporary files, cleaned up on replacement or window close.

- **Video pixel scale** offers 25%, 50%, 100%, 150%, and 200%, preserving proportions.
  This resizes the decoded source pixels using Lanczos interpolation. Upscaling
  increases pixel dimensions but cannot recover missing source detail. Scaled frames
  are limited to 33 megapixels and 16384 pixels on either side.
- **Video preview FPS** initially uses the source average rate (limited to 1–120).
  Lower it for fewer sampled frames and lighter playback. Frame N samples time
  `(N - 1) / FPS`; variable-frame-rate sources are sampled on this regular timeline.
- **Resolution format** and **Width (characters)** independently control ASCII
  dimensions. For example, 1080p maps to 240 columns by 68 rows. These are text
  dimensions, not the resolution of an exported MP4.
- **Frame**, **Play / Pause**, and **Loop** work with movies. Original and ASCII
  views show the same frame; RGB, contrast, fit preview, and manual zoom still apply.
- Pixel scale and FPS changes reset to the first frame and regenerate automatically.
  Format changes regenerate the current frame. Other conversion options use
  **Generate preview** as before.

Playback is a silent, best-effort preview. This first decoder seeks and launches
FFmpeg for each requested frame, so high FPS or large resolutions can play slower
than real time. Lower the FPS, pixel scale, or ASCII width when needed. Pause also
works while a frame is decoding; that frame may finish but playback will stop.
Decoding has a 30-second timeout per request.

TXT and HTML export the selected movie frame with a numbered filename, just like
GIFs. Whole-movie export, audio playback, and downloading video URLs are not included.

## Command line

The batch CLI supports still images, all GIF frames, and sampled MP4 frames:

```powershell
# Convert a directory using a reusable preset/configuration.
.\run-cli.ps1 --batch .\Images --config .\configs\batch.yaml

# Override settings for this run; JSON and YAML use the same schema.
.\run-cli.ps1 --batch .\Media --recursive --config .\configs\batch.json --preset 1080p --columns 200 --fps 6 --max-frames 120

# Save plain text files (default output format).
.\run-cli.ps1 --batch .\Images --preset hd --output-dir .\ascii\text

# Pipe a single ASCII image or structured frames into another workflow.
.\run-cli.ps1 .\Images\img.png --columns 100 --stdout > picture.txt
.\run-cli.ps1 --batch .\Media --recursive --format jsonl --color --fps 6 --stdout > frames.jsonl

# Preview the options.
.\run-cli.ps1 --help
```

The launcher compiles the CLI to `out/cli` and preserves your working directory.
For repeated runs, compile once and invoke Java directly:

```powershell
javac -d out/cli src/ImageInspect.java src/VideoSource.java src/ConversionConfig.java src/AsciiCli.java
java -cp out/cli AsciiCli --batch Images --preset hd --stdout
```

MP4s need FFmpeg/ffprobe as described above. When running outside the project root,
set `FFMPEG_PATH` and `FFPROBE_PATH` to absolute executable paths or put them on PATH.

### Configuration and precedence

Examples: [`configs/batch.json`](configs/batch.json) and
[`configs/batch.yaml`](configs/batch.yaml). Both describe the same conversion.
Configurations are flat mappings of scalar values. JSON strings/numbers/booleans
and YAML plain/quoted scalars and comments are supported. Nested mappings, arrays,
YAML anchors/tags, multiple documents, and null values are not supported. Unknown
or duplicate keys and invalid values produce a settings error before conversion.

Explicit CLI settings override config values, which override defaults. The selected
preset supplies the default width only when `columns` is not explicitly set in either
the config or CLI. Config `outputDir` is relative to the config file; CLI paths are
relative to the current working directory. Input is always supplied on the command line.

| Config key | CLI option | Default / meaning |
| --- | --- | --- |
| `preset` | `--preset` | `original`; also `sd`, `hd`/`720p`, `full-hd`/`1080p`, `qhd`, `uhd`/`4k`, `square`, `portrait`, `vertical` |
| `columns` | `--columns` | Preset width; 1–2000 characters, at most 4 million cells per frame |
| `autoContrast` | `--contrast` / `--no-contrast` | `true` |
| `clip` | `--clip` | `0.02`, fraction clipped at each end; 0 through 0.499999 |
| `invert` | `--invert` / `--no-invert` | `false` |
| `color` | `--color` / `--no-color` | `false`; supported by every format except TXT |
| `fps` | `--fps` | `0`: source average FPS limited to 1–120; otherwise 1–120, MP4 only |
| `scale` | `--scale` | `1.0`; 0.125–4.0 decoded pixel scale, MP4 only |
| `maxFrames` | `--max-frames` | `0`: all frames; positive integer limits each input |
| `format` | `--format` | `txt` (default), `html`, `ansi`, `bbcode`, `markdown`, `svg`, or `jsonl` |
| `outputDir` | `--output-dir` | `ascii` in the working directory |
| `recursive` | `--recursive` / `--no-recursive` | `false` |
| `overwrite` | `--overwrite` / `--no-overwrite` | `false` |

### Batch files and pipelines

Batch discovery is sorted and supports PNG, JPG/JPEG, BMP, GIF, and MP4 extensions
(case insensitive). It skips other extensions and does not follow symlinks.
Each input gets its own output directory, preserving the input filename **including
its extension** and any relative subdirectories:

```text
ascii/
  photo.png/frame-000001.txt
  photo.jpg/frame-000001.txt
  clips/movie.mp4/frame-000001.txt
  clips/movie.mp4/frame-000002.txt
```

Existing files cause that input to fail unless `--overwrite` is set. Other inputs
continue. Frames already written remain after a failure; rerun with `--overwrite`
to replace them. Overwrite does not remove older extra frames from previous runs.
MP4 frames are processed one at a time; GIF input still uses the existing in-memory
compositor. Large movies may take substantial time with the current per-frame decoder.

`--stdout` writes **no output files**. TXT mode emits only ASCII, with a form-feed
character (`\f`, U+000C) between frames and no headers. ANSI, BBCode, and Markdown
use the same separator between independently formatted frames. JSONL emits one JSON object
per frame with `source`, one-based `frame`, `timeMillis`, `delayMillis`, `columns`,
`rows`, and `text`. With color enabled, `rgb` is a row-major array of packed
`0xRRGGBB` integers. Newlines inside `text` are JSON-escaped. GIF timestamps use
original delays; MP4 timestamps use the selected sampling rate. HTML and SVG stdout
require a single still image, or a single GIF/MP4 input with `--max-frames 1`;
batch stdout for these document formats is rejected to avoid concatenating invalid
documents. All formats support batch output to separate files. Java emits UTF-8;
older PowerShell versions may re-encode redirected
text, so choose the receiving tool's encoding explicitly when needed.

Progress/errors go to stderr. Exit codes are **0** for success, **1** for conversion
or output failures (including an empty batch), and **2** for invalid arguments/config.
Use PowerShell's `$LASTEXITCODE` to check the result. The CLI does not export encoded
video or audio; movie output is a sequence of ASCII frame files or streamed records.

```powershell
# Display RGB ASCII in a compatible terminal.
.\run-cli.ps1 .\Images\img.png --format ansi --color --stdout

# Export vector ASCII or styled Markdown in batches.
.\run-cli.ps1 --batch .\Images --format svg --color --output-dir .\ascii\vectors
.\run-cli.ps1 --batch .\Images --format markdown --color --output-dir .\ascii\markdown

# Export BBCode, or pipe a single SVG document.
.\run-cli.ps1 .\Images\img.png --format bbcode --color
.\run-cli.ps1 .\Images\img.png --format svg --color --stdout > art.svg
```

The original image/GIF launcher remains available:

```powershell
java src/ImageInspect.java Images/img.png 160
```

For GIF input, the command line exports every composed frame as a numbered TXT file.
Single-frame images keep the original `<image-name>.txt` naming.

## Converter checks

```powershell
javac -d out/ascii-checks src/ImageInspect.java tests/ImageInspectTest.java
java -cp out/ascii-checks ImageInspectTest
```

To include dashboard and color checks:

```powershell
javac -d out/ascii-checks src/ImageInspect.java src/VideoSource.java src/AsciiDashboard.java tests/ImageInspectTest.java tests/AsciiDashboardTest.java tests/AsciiColorTest.java tests/GifFramesTest.java tests/VideoSourceTest.java
java -cp out/ascii-checks ImageInspectTest
java -cp out/ascii-checks AsciiDashboardTest
java -cp out/ascii-checks AsciiColorTest
java -cp out/ascii-checks GifFramesTest
java -cp out/ascii-checks VideoSourceTest
```

The MP4 test requires FFmpeg and generates its own short video fixture. It checks
decoding, seeking, up/downscaling, final-frame access, and dashboard playback.

Batch/config/pipeline checks (also require FFmpeg for the generated MP4 fixture):

```powershell
javac -d out/batch-checks src/ImageInspect.java src/VideoSource.java src/ConversionConfig.java src/AsciiCli.java tests/GifFramesTest.java tests/AsciiCliTest.java
java -cp out/batch-checks AsciiCliTest
```

Contributor guidance for future work is in [`AGENTS.md`](AGENTS.md).

Export format checks:

```powershell
javac -d out/format-checks src/*.java tests/*.java
java -cp out/format-checks ExportFormatsTest
```
