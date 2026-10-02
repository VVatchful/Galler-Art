import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public class AsciiDashboard extends JFrame {
    private final JButton choose = new JButton("Choose image...");
    private final JButton convert = new JButton("Generate preview");
    private final JButton export = new JButton("Export TXT");
    private final JButton exportHtml = new JButton("Export HTML");
    private final JCheckBox color = new JCheckBox("Use original RGB colors", false);
    private final JSpinner columns = new JSpinner(new SpinnerNumberModel(160, 10, 1000, 10));
    private final JComboBox<ImageInspect.OutputFormat> format =
            new JComboBox<>(ImageInspect.OutputFormat.values());
    private final JLabel outputDimensions = new JLabel("Select an image to see output size");
    private final JCheckBox contrast = new JCheckBox("Enhance contrast", true);
    private final JSpinner clipping = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 20.0, 0.5));
    private final JCheckBox invert = new JCheckBox("Invert brightness");
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(8, 4, 24, 1));
    private final JTextArea preview = new JTextArea();
    private final JCheckBox fitPreview = new JCheckBox("Fit preview to window", true);
    private final FittedPreview fittedPreview = new FittedPreview();
    private final FittedPreview zoomPreview = new FittedPreview();
    private final CardLayout previewLayout = new CardLayout();
    private final JPanel previewViews = new JPanel(previewLayout);
    private final JLabel status = new JLabel("Choose an image to get started.");
    private final JLabel filename = new JLabel("No image selected");
    private final JSpinner frame = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final JLabel frameInfo = new JLabel("No frames loaded");
    private final JButton play = new JButton("Play");
    private final JCheckBox loop = new JCheckBox("Loop", true);
    private final Timer playbackTimer = new Timer(100, event -> advancePlayback());
    private boolean playing;
    private boolean advancingFrame;
    private final ImagePanel original = new ImagePanel();
    private List<ImageInspect.ImageFrame> sourceFrames = List.of();
    private List<ImageInspect.AsciiResult> generatedFrames = List.of();
    private BufferedImage source;
    private File sourceFile;
    private String generated;
    private ImageInspect.AsciiResult generatedResult;
    private boolean busy;

    public AsciiDashboard() {
        super("Galler-Art | ASCII Studio");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1200, 800);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(20, 20, 16, 20));
        setContentPane(root);
        JLabel title = new JLabel("Galler-Art  /  ASCII Studio");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        root.add(title, BorderLayout.NORTH);

        JPanel settings = new JPanel();
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        addControl(settings, new JLabel("SOURCE IMAGE"));
        addControl(settings, choose);
        addControl(settings, filename);
        original.setPreferredSize(new Dimension(220, 165));
        original.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165));
        addControl(settings, original);
        addControl(settings, row("Frame", frame));
        addControl(settings, frameInfo);
        JPanel playbackControls = new JPanel(new GridLayout(1, 2, 8, 0));
        playbackControls.add(play);
        playbackControls.add(loop);
        addControl(settings, playbackControls);
        play.setToolTipText("Play or pause the original and ASCII frames together.");
        loop.setToolTipText("Repeat continuously; turn off to stop after the last frame.");
        addControl(settings, new JLabel("OUTPUT OPTIONS"));
        addControl(settings, format);
        format.setToolTipText("Resolution presets set proportions and text detail. The whole image fits with padding.");
        addControl(settings, row("Width (characters)", columns));
        addControl(settings, outputDimensions);
        addControl(settings, new JLabel("Fit whole image / pad to format"));
        addControl(settings, contrast);
        addControl(settings, row("Clip each end (%)", clipping));
        clipping.setToolTipText("Trim extreme shadows and highlights before stretching grayscale.");
        addControl(settings, invert);
        addControl(settings, color);
        addControl(settings, fitPreview);
        addControl(settings, row("Preview font size", fontSize));
        addControl(settings, convert);
        addControl(settings, export);
        addControl(settings, exportHtml);
        settings.add(Box.createVerticalGlue());
        JScrollPane settingsScroll = new JScrollPane(settings);
        settingsScroll.setPreferredSize(new Dimension(315, 640));
        settingsScroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(settingsScroll, BorderLayout.WEST);

        preview.setEditable(false);
        preview.setLineWrap(false);
        preview.setBackground(new Color(18, 22, 29));
        preview.setForeground(new Color(232, 237, 244));
        preview.setMargin(new Insets(12, 12, 12, 12));
        updateFont();
        zoomPreview.fit = false;
        JScrollPane scroll = new JScrollPane(zoomPreview);
        previewViews.add(fittedPreview, "fit");
        previewViews.add(scroll, "text");
        previewViews.setBorder(BorderFactory.createTitledBorder("ASCII preview"));
        root.add(previewViews, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);

        choose.addActionListener(event -> chooseImage());
        playbackTimer.setRepeats(false);
        play.addActionListener(event -> togglePlayback());
        frame.addChangeListener(event -> {
            if (!advancingFrame) pausePlayback();
            showFrame();
        });
        convert.addActionListener(event -> generate());
        export.addActionListener(event -> exportText());
        exportHtml.addActionListener(event -> exportHtml());
        color.addActionListener(event -> refreshColor());
        columns.addChangeListener(event -> invalidatePreview());
        format.addActionListener(event -> {
            ImageInspect.OutputFormat selected = (ImageInspect.OutputFormat) format.getSelectedItem();
            columns.setValue(selected.columns);
            invalidatePreview();
            generate();
        });
        clipping.addChangeListener(event -> invalidatePreview());
        contrast.addActionListener(event -> {
            clipping.setEnabled(contrast.isSelected());
            invalidatePreview();
        });
        invert.addActionListener(event -> invalidatePreview());
        fontSize.addChangeListener(event -> updateFont());
        fitPreview.addActionListener(event -> updatePreviewMode());
        updatePreviewMode();
        setBusy(false);
    }

    private static JPanel row(String label, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(input, BorderLayout.EAST);
        return panel;
    }

    private static void addControl(JPanel panel, JComponent control) {
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!(control instanceof ImagePanel)) {
            control.setMaximumSize(new Dimension(Integer.MAX_VALUE, control.getPreferredSize().height));
        }
        panel.add(control);
        panel.add(Box.createVerticalStrut(12));
    }

    private void updateFont() {
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, (Integer) fontSize.getValue()));
        zoomPreview.setFont(preview.getFont());
        zoomPreview.revalidate();
        zoomPreview.repaint();
    }

    private void refreshColor() {
        fittedPreview.setColors(color.isSelected() ? generatedResult : null);
        zoomPreview.setColors(color.isSelected() ? generatedResult : null);
    }

    private void updatePreviewMode() {
        previewLayout.show(previewViews, fitPreview.isSelected() ? "fit" : "text");
        fontSize.setEnabled(!fitPreview.isSelected());
    }

    private void invalidatePreview() {
        pausePlayback();
        updateOutputDimensions();
        generatedFrames = List.of();
        updatePlaybackControls();
        generated = null;
        generatedResult = null;
        preview.setText("");
        fittedPreview.setAscii("");
        zoomPreview.setAscii("");
        refreshColor();
        export.setEnabled(false);
        exportHtml.setEnabled(false);
        if (source != null) {
            status.setText("Options changed. Generate a new preview to export these settings.");
        }
    }

    private void updateOutputDimensions() {
        if (source == null) return;
        int[] size = ImageInspect.outputSize(source, (Integer) columns.getValue(),
                (ImageInspect.OutputFormat) format.getSelectedItem());
        outputDimensions.setText(size[0] + " columns x " + size[1] + " rows (text)");
    }

    private void setBusy(boolean value) {
        if (value) pausePlayback();
        busy = value;
        choose.setEnabled(!busy);
        convert.setEnabled(!busy && source != null);
        export.setEnabled(!busy && generated != null);
        exportHtml.setEnabled(!busy && generated != null);
        columns.setEnabled(!busy);
        format.setEnabled(!busy && source != null);
        contrast.setEnabled(!busy);
        clipping.setEnabled(!busy && contrast.isSelected());
        invert.setEnabled(!busy);
        frame.setEnabled(!busy && sourceFrames.size() > 1);
        updatePlaybackControls();
    }

    private boolean canPlay() {
        return !busy && sourceFrames.size() > 1 && generatedFrames.size() == sourceFrames.size();
    }

    private void updatePlaybackControls() {
        play.setEnabled(canPlay());
        play.setText(playing ? "Pause" : "Play");
        loop.setEnabled(canPlay());
    }

    private void togglePlayback() {
        if (playing) {
            pausePlayback();
        } else if (canPlay()) {
            // Replaying a completed sequence starts from the beginning.
            if ((Integer) frame.getValue() == sourceFrames.size()) frame.setValue(1);
            playing = true;
            updatePlaybackControls();
            scheduleNextFrame();
        }
    }

    private void pausePlayback() {
        playing = false;
        playbackTimer.stop();
        updatePlaybackControls();
    }

    private void scheduleNextFrame() {
        int delay = sourceFrames.get((Integer) frame.getValue() - 1).delayMillis;
        // A missing/zero delay must not cause a tight event-loop spin.
        playbackTimer.setInitialDelay(delay > 0 ? delay : 100);
        playbackTimer.restart();
    }

    private void advancePlayback() {
        if (!playing || !canPlay()) {
            pausePlayback();
            return;
        }
        int current = (Integer) frame.getValue();
        if (current == sourceFrames.size() && !loop.isSelected()) {
            pausePlayback();
            return;
        }
        advancingFrame = true;
        try {
            frame.setValue(current == sourceFrames.size() ? 1 : current + 1);
        } finally {
            advancingFrame = false;
        }
        scheduleNextFrame();
    }

    @Override
    public void dispose() {
        pausePlayback();
        super.dispose();
    }

    private void chooseImage() {
        pausePlayback();
        JFileChooser chooser = new JFileChooser(new File("Images"));
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, BMP, GIF)",
                "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        loadImage(chooser.getSelectedFile());
    }

    void loadImage(File selected) {
        if (busy) return;
        setBusy(true);
        status.setText("Loading " + selected.getName() + "...");
        new SwingWorker<List<ImageInspect.ImageFrame>, Void>() {
            protected List<ImageInspect.ImageFrame> doInBackground() throws Exception {
                return ImageInspect.readFrames(selected);
            }

            protected void done() {
                boolean loaded = false;
                try {
                    List<ImageInspect.ImageFrame> loadedFrames = get();
                    generatedFrames = List.of();
                    sourceFrames = loadedFrames;
                    frame.setModel(new SpinnerNumberModel(1, 1, sourceFrames.size(), 1));
                    source = sourceFrames.get(0).image;
                    sourceFile = selected;
                    filename.setText(selected.getName());
                    filename.setToolTipText(selected.getAbsolutePath());
                    original.image = source;
                    original.repaint();
                    updateOutputDimensions();
                    generated = null;
                    generatedResult = null;
                    preview.setText("");
                    fittedPreview.setAscii("");
                    zoomPreview.setAscii("");
                    refreshColor();
                    showFrame();
                    loaded = true;
                } catch (Exception exception) {
                    showError(exception);
                } finally {
                    setBusy(false);
                }
                if (loaded) generate();
            }
        }.execute();
    }

    private void generate() {
        if (source == null || busy) return;
        try {
            columns.commitEdit();
            clipping.commitEdit();
        } catch (java.text.ParseException exception) {
            showError(new IllegalArgumentException("Enter valid numeric options."));
            return;
        }
        int width = (Integer) columns.getValue();
        boolean enhance = contrast.isSelected();
        double clip = ((Number) clipping.getValue()).doubleValue() / 100;
        boolean reverse = invert.isSelected();
        ImageInspect.OutputFormat selectedFormat = (ImageInspect.OutputFormat) format.getSelectedItem();
        setBusy(true);
        status.setText("Generating ASCII...");
        new SwingWorker<List<ImageInspect.AsciiResult>, Void>() {
            protected List<ImageInspect.AsciiResult> doInBackground() {
                List<ImageInspect.AsciiResult> results = new ArrayList<>();
                if (sourceFrames.isEmpty()) {
                    results.add(ImageInspect.convert(source, width, enhance, clip, reverse, selectedFormat));
                } else {
                    for (ImageInspect.ImageFrame sourceFrame : sourceFrames) {
                        results.add(ImageInspect.convert(sourceFrame.image, width, enhance, clip, reverse, selectedFormat));
                    }
                }
                return List.copyOf(results);
            }

            protected void done() {
                try {
                    generatedFrames = get();
                    showFrame();
                } catch (Exception exception) {
                    generatedFrames = List.of();
                    generated = null;
                    showError(exception);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void showFrame() {
        int index = (Integer) frame.getValue() - 1;
        if (!sourceFrames.isEmpty()) {
            ImageInspect.ImageFrame selected = sourceFrames.get(index);
            source = selected.image;
            original.image = source;
            original.repaint();
            frameInfo.setText("Frame " + (index + 1) + " of " + sourceFrames.size()
                    + " | " + selected.delayMillis + " ms");
        }
        generatedResult = generatedFrames.isEmpty() ? null : generatedFrames.get(index);
        generated = generatedResult == null ? null : generatedResult.text;
        String text = generated == null ? "" : generated;
        preview.setText(text);
        fittedPreview.setAscii(text);
        zoomPreview.setAscii(text);
        refreshColor();
        preview.setCaretPosition(0);
        export.setEnabled(!busy && generated != null);
        exportHtml.setEnabled(!busy && generated != null);
        if (generatedResult != null) {
            status.setText(source.getWidth() + " x " + source.getHeight() + " pixels | "
                    + generatedResult.width + " columns x " + generatedResult.height + " rows | "
                    + generatedFrames.size() + " frame(s) converted. Export saves the selected frame.");
        }
    }

    private void exportText() {
        exportFile(false);
    }

    private void exportHtml() {
        exportFile(true);
    }

    private void exportFile(boolean html) {
        if (generated == null || busy) return;
        pausePlayback();
        try {
            Path folder = Path.of("ascii");
            Files.createDirectories(folder);
            String name = sourceFile.getName();
            int dot = name.lastIndexOf('.');
            String suffix = sourceFrames.size() > 1 ? String.format("-frame-%04d", (Integer) frame.getValue()) : "";
            Path output = folder.resolve((dot > 0 ? name.substring(0, dot) : name) + suffix + (html ? ".html" : ".txt"));
            Files.writeString(output, html ? generatedResult.toHtml(color.isSelected()) : generated, StandardCharsets.UTF_8);
            status.setText("Saved " + output.toAbsolutePath());
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void showError(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Unable to complete action: " + cause.getMessage());
        JOptionPane.showMessageDialog(this, cause.getMessage(), "ASCII Studio", JOptionPane.ERROR_MESSAGE);
    }

    static class FittedPreview extends JPanel {
        private String[] rows = new String[0];
        private int columns;
        private ImageInspect.AsciiResult colors;
        boolean fit = true;

        FittedPreview() {
            setBackground(Color.BLACK);
            setForeground(new Color(232, 237, 244));
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        }

        void setAscii(String ascii) {
            rows = ascii.isEmpty() ? new String[0] : ascii.split("\n");
            columns = 0;
            for (String row : rows) columns = Math.max(columns, row.length());
            revalidate();
            repaint();
        }

        void setColors(ImageInspect.AsciiResult value) {
            colors = value;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (fit) return new Dimension(400, 300);
            int cellWidth = getFontMetrics(getFont()).charWidth('M');
            return new Dimension(columns * cellWidth + 24, rows.length * cellWidth * 2 + 24);
        }

        // Fit the complete character grid, including whitespace padding, at a 1:2 cell ratio.
        Rectangle fittedBounds() {
            if (columns == 0 || rows.length == 0) return new Rectangle();
            if (!fit) {
                Dimension size = getPreferredSize();
                return new Rectangle(12, 12, size.width - 24, size.height - 24);
            }
            double scale = Math.min(Math.max(0, getWidth() - 24) / (double) columns,
                    Math.max(0, getHeight() - 24) / (2.0 * rows.length));
            int width = (int) Math.floor(columns * scale);
            int height = (int) Math.floor(rows.length * 2 * scale);
            return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Rectangle bounds = fittedBounds();
            if (bounds.width <= 0 || bounds.height <= 0) return;
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                copy.setFont(getFont());
                copy.setColor(getForeground());
                FontMetrics metrics = copy.getFontMetrics();
                double cellWidth = metrics.charWidth('M');
                double cellHeight = cellWidth * 2;
                copy.translate(bounds.x, bounds.y);
                copy.scale(bounds.width / (columns * cellWidth), bounds.height / (rows.length * cellHeight));
                double baseline = (cellHeight - metrics.getHeight()) / 2 + metrics.getAscent();
                for (int y = 0; y < rows.length; y++) {
                    if (colors == null) {
                        copy.drawString(rows[y], 0f, (float) (y * cellHeight + baseline));
                    } else {
                        for (int x = 0; x < rows[y].length(); x++) {
                            if (rows[y].charAt(x) == ' ') continue;
                            copy.setColor(new Color(colors.rgbAt(x, y)));
                            copy.drawString(String.valueOf(rows[y].charAt(x)), (float) (x * cellWidth),
                                    (float) (y * cellHeight + baseline));
                        }
                    }
                }
            } finally {
                copy.dispose();
            }
        }
    }

    private static class ImagePanel extends JPanel {
        BufferedImage image;

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) return;
            double scale = Math.min((double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight());
            int width = Math.max(1, (int) (image.getWidth() * scale));
            int height = Math.max(1, (int) (image.getHeight() * scale));
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            copy.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
            copy.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AsciiDashboard().setVisible(true));
    }
}
