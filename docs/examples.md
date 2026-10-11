# Reproducing before/after screenshots

The PNGs in `images/` are offscreen Swing captures: the existing `Images/img.png`
appears at left, and the actual `AsciiDashboard.FittedPreview` component at right.
They are not mockups or full-desktop screenshots. Source and conversion are rendered
by `GenerateExamples.java`; no generated art or hand-painted ASCII is used.

```powershell
javac -d out/doc-examples src/*.java docs/GenerateExamples.java
java -cp out/doc-examples GenerateExamples
```

Run from the project root. Common settings: 160 columns, original proportions,
automatic contrast with 2% clipping, inversion off, and fit-to-panel preview.

| Screenshot | Color | Edges | Dithering | Palette |
| --- | --- | --- | --- | --- |
| `grayscale.png` | Off | None | None | ` .:-=+*#%@` |
| `color.png` | On | None | None | ` .:-=+*#%@` |
| `dither.png` | Off | None | Floyd–Steinberg | space followed by `@` |
| `edges.png` | Off | Canny | None | space followed by `@` |

Each screenshot is 1320×560 pixels. Font rendering may vary by JDK/platform.
The source image already contains clipped lettering at its left edge; conversion
does not crop the source. The files are documentation artifacts; generated classes
remain under ignored `out/`.
