import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public class GifFramesTest {
    private static final int[] PALETTE = {
            0xff000000, 0xffff0000, 0xff00ff00, 0xff0000ff,
            0xffffffff, 0xffffff00, 0xff00ffff, 0xffff00ff
    };

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("galler-art-frames-", ".gif");
        try {
            write(file,
                    new Patch(6, 4, 1, 0, 0, "doNotDispose", 3, false),
                    new Patch(2, 2, 2, 2, 0, "restoreToPrevious", 7, false),
                    new Patch(2, 2, 3, 4, 2, "none", 0, false),
                    new Patch(2, 2, 4, 0, 0, "restoreToBackgroundColor", 12, false),
                    new Patch(2, 2, 2, 2, 2, "doNotDispose", 65535, false));
            List<ImageInspect.ImageFrame> frames = ImageInspect.readFrames(file.toFile());
            check(frames.size() == 5, "Every GIF frame decoded");
            int[] delays = {30, 70, 0, 120, 655350};
            for (int i = 0; i < frames.size(); i++) {
                check(frames.get(i).image.getWidth() == 6 && frames.get(i).image.getHeight() == 4,
                        "All frames use the logical screen size");
                check(frames.get(i).delayMillis == delays[i], "Exact delay in milliseconds");
            }
            pixel(frames, 0, 2, 0, PALETTE[1], "First snapshot is independent");
            pixel(frames, 1, 2, 0, PALETTE[2], "Offset patch drawn");
            pixel(frames, 1, 0, 3, PALETTE[1], "Unchanged pixels retained");
            pixel(frames, 2, 2, 0, PALETTE[1], "Previous canvas restored");
            pixel(frames, 2, 4, 2, PALETTE[3], "Vertical and horizontal offsets");
            pixel(frames, 3, 0, 0, PALETTE[4], "Capture before disposal");
            pixel(frames, 4, 0, 0, PALETTE[5], "Restore global background color");
            pixel(frames, 4, 4, 2, PALETTE[3], "Background disposal clears only patch rectangle");
            for (ImageInspect.ImageFrame frame : frames) {
                ImageInspect.AsciiResult ascii = ImageInspect.convert(frame.image, 6, false, 0, false,
                        ImageInspect.OutputFormat.ORIGINAL);
                check(ascii.width == 6 && ascii.height == 2, "Stable ASCII dimensions");
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 6; x++) {
                        check(ascii.rgbAt(x, y) == (frame.image.getRGB(x, y * 2) & 0xffffff),
                                "ASCII colors come from composed frame");
                    }
                }
            }

            Patch transparent = new Patch(2, 2, 0, 2, 0, "doNotDispose", 2, true);
            transparent.image.getRaster().setSample(0, 0, 0, 2);
            Patch overlay = new Patch(2, 2, 0, 2, 0, "restoreToBackgroundColor", 4, true);
            overlay.image.getRaster().setSample(1, 0, 0, 3);
            write(file, transparent, overlay, new Patch(2, 2, 2, 0, 2, "none", 0, true));
            frames = ImageInspect.readFrames(file.toFile());
            pixel(frames, 0, 0, 0, 0, "Initial uncovered canvas is transparent");
            pixel(frames, 1, 2, 0, PALETTE[2], "Transparent patch preserves earlier pixel");
            pixel(frames, 1, 3, 0, PALETTE[3], "Opaque pixel overlays earlier frame");
            pixel(frames, 2, 2, 0, 0, "Transparent background disposal clears earlier pixel");
            ImageInspect.AsciiResult ascii = ImageInspect.convert(frames.get(2).image, 6, false, 0, false,
                    ImageInspect.OutputFormat.ORIGINAL);
            check(ascii.rgbAt(2, 0) == 0 && ascii.text.charAt(2) == ' ', "Transparent cells convert onto black");

            Patch local = new Patch(2, 2, 1, 2, 0, "none", 1, false);
            int[] localPalette = PALETTE.clone();
            localPalette[1] = 0xff123456;
            local.image = indexed(2, 2, 1, false, localPalette);
            write(file, local);
            frames = ImageInspect.readFrames(file.toFile());
            pixel(frames, 0, 2, 0, 0xff123456, "Local color table overrides global table");
            pixel(frames, 0, 0, 0, PALETTE[5], "Opaque partial first frame uses global background");

            Patch interlaced = new Patch(6, 16, 1, 0, 0, "none", 1, false);
            interlaced.interlaced = true;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 6; x++) interlaced.image.getRaster().setSample(x, y, 0, y % 7 + 1);
            }
            write(file, interlaced);
            frames = ImageInspect.readFrames(file.toFile());
            for (int y = 0; y < 16; y++) pixel(frames, 0, 0, y, PALETTE[y % 7 + 1], "Interlaced row order");

            BufferedImage png = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
            png.setRGB(1, 1, 0x80abcdef);
            ImageIO.write(png, "png", file.toFile());
            frames = ImageInspect.readFrames(file.toFile());
            check(frames.size() == 1 && frames.get(0).delayMillis == 0, "Still image has one frame");
            pixel(frames, 0, 1, 1, 0x80abcdef, "Still image alpha preserved; detection uses file contents");
            Files.writeString(file, "not an image");
            try {
                ImageInspect.readFrames(file.toFile());
                throw new AssertionError("Unsupported input must fail");
            } catch (java.io.IOException expected) {
                // Expected; the same path can be overwritten, so the reader released its file handle.
            }
            System.out.println("GIF checks passed: offsets, disposal, transparency, palettes, timing, ASCII, still images.");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    static class Patch {
        BufferedImage image;
        final int left, top, delay;
        final String disposal;
        final boolean transparent;
        boolean interlaced;

        Patch(int width, int height, int index, int left, int top, String disposal, int delay, boolean transparent) {
            image = indexed(width, height, index, transparent, PALETTE);
            this.left = left;
            this.top = top;
            this.delay = delay;
            this.disposal = disposal;
            this.transparent = transparent;
        }
    }

    private static BufferedImage indexed(int width, int height, int index, boolean transparent, int[] palette) {
        IndexColorModel colors = new IndexColorModel(3, palette.length, palette, 0, false,
                transparent ? 0 : -1, java.awt.image.DataBuffer.TYPE_BYTE);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colors);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.getRaster().setSample(x, y, 0, index);
        }
        return image;
    }

    static void write(Path file, Patch... patches) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(output);
            IIOMetadata stream = writer.getDefaultStreamMetadata(null);
            IIOMetadataNode root = (IIOMetadataNode) stream.getAsTree("javax_imageio_gif_stream_1.0");
            IIOMetadataNode screen = node(root, "LogicalScreenDescriptor");
            screen.setAttribute("logicalScreenWidth", "6");
            int height = 4;
            for (Patch patch : patches) height = Math.max(height, patch.top + patch.image.getHeight());
            screen.setAttribute("logicalScreenHeight", Integer.toString(height));
            screen.setAttribute("colorResolution", "8");
            IIOMetadataNode table = node(root, "GlobalColorTable");
            table.setAttribute("sizeOfGlobalColorTable", "8");
            table.setAttribute("backgroundColorIndex", "5");
            table.setAttribute("sortFlag", "FALSE");
            for (int i = 0; i < PALETTE.length; i++) {
                IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                entry.setAttribute("index", Integer.toString(i));
                entry.setAttribute("red", Integer.toString((PALETTE[i] >> 16) & 255));
                entry.setAttribute("green", Integer.toString((PALETTE[i] >> 8) & 255));
                entry.setAttribute("blue", Integer.toString(PALETTE[i] & 255));
                table.appendChild(entry);
            }
            stream.setFromTree("javax_imageio_gif_stream_1.0", root);
            writer.prepareWriteSequence(stream);
            for (Patch patch : patches) {
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        javax.imageio.ImageTypeSpecifier.createFromRenderedImage(patch.image), null);
                IIOMetadataNode image = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                IIOMetadataNode local = node(image, "LocalColorTable");
                while (local.hasChildNodes()) local.removeChild(local.getFirstChild());
                local.setAttribute("sizeOfLocalColorTable", "8");
                local.setAttribute("sortFlag", "FALSE");
                IndexColorModel colors = (IndexColorModel) patch.image.getColorModel();
                for (int i = 0; i < 8; i++) {
                    IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", Integer.toString(i));
                    entry.setAttribute("red", Integer.toString(colors.getRed(i)));
                    entry.setAttribute("green", Integer.toString(colors.getGreen(i)));
                    entry.setAttribute("blue", Integer.toString(colors.getBlue(i)));
                    local.appendChild(entry);
                }
                IIOMetadataNode descriptor = node(image, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", Integer.toString(patch.left));
                descriptor.setAttribute("imageTopPosition", Integer.toString(patch.top));
                descriptor.setAttribute("interlaceFlag", patch.interlaced ? "TRUE" : "FALSE");
                IIOMetadataNode control = node(image, "GraphicControlExtension");
                control.setAttribute("disposalMethod", patch.disposal);
                control.setAttribute("delayTime", Integer.toString(patch.delay));
                control.setAttribute("transparentColorFlag", patch.transparent ? "TRUE" : "FALSE");
                control.setAttribute("transparentColorIndex", "0");
                metadata.setFromTree("javax_imageio_gif_image_1.0", image);
                writer.writeToSequence(new IIOImage(patch.image, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode node(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equals(name)) return (IIOMetadataNode) parent.item(i);
        }
        IIOMetadataNode child = new IIOMetadataNode(name);
        parent.appendChild(child);
        return child;
    }

    private static void pixel(List<ImageInspect.ImageFrame> frames, int frame, int x, int y, int expected, String message) {
        int actual = frames.get(frame).image.getRGB(x, y);
        check(actual == expected, message + ": expected " + Integer.toHexString(expected) + ", got " + Integer.toHexString(actual));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
