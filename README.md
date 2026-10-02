# Galler-Art

A Java image-to-ASCII converter with a small desktop dashboard. Requires JDK 11 or newer.

## Dashboard

From PowerShell in the project folder:

```powershell
.\run-dashboard.ps1
```

Or run `AsciiDashboard.main()` in IntelliJ. You can also compile and launch manually:

```powershell
javac -d out/dashboard src/ImageInspect.java src/AsciiDashboard.java
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
Automatic playback, animated export, and video conversion are not implemented yet.

## Command line

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
javac -d out/ascii-checks src/ImageInspect.java src/AsciiDashboard.java tests/ImageInspectTest.java tests/AsciiDashboardTest.java tests/AsciiColorTest.java tests/GifFramesTest.java
java -cp out/ascii-checks ImageInspectTest
java -cp out/ascii-checks AsciiDashboardTest
java -cp out/ascii-checks AsciiColorTest
java -cp out/ascii-checks GifFramesTest
```
