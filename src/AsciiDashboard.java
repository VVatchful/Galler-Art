import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public class AsciiDashboard extends JFrame {
    private final JButton choose = new JButton("Choose image...");
    private final JButton convert = new JButton("Generate preview");
    private final JButton export = new JButton("Export TXT");
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
    private final CardLayout previewLayout = new CardLayout();
    private final JPanel previewViews = new JPanel(previewLayout);
    private final JLabel status = new JLabel("Choose an image to get started.");
    private final JLabel filename = new JLabel("No image selected");
    private final ImagePanel original = new ImagePanel();
    private BufferedImage source;
    private File sourceFile;
    private String generated;
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
        settings.setPreferredSize(new Dimension(290, 640));
        addControl(settings, new JLabel("SOURCE IMAGE"));
        addControl(settings, choose);
        addControl(settings, filename);
        original.setPreferredSize(new Dimension(220, 165));
        original.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165));
        addControl(settings, original);
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
        addControl(settings, fitPreview);
        addControl(settings, row("Preview font size", fontSize));
        addControl(settings, convert);
        addControl(settings, export);
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
        JScrollPane scroll = new JScrollPane(preview);
        previewViews.add(fittedPreview, "fit");
        previewViews.add(scroll, "text");
        previewViews.setBorder(BorderFactory.createTitledBorder("ASCII preview"));
        root.add(previewViews, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);

        choose.addActionListener(event -> chooseImage());
        convert.addActionListener(event -> generate());
        export.addActionListener(event -> exportText());
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
    }

    private void updatePreviewMode() {
        previewLayout.show(previewViews, fitPreview.isSelected() ? "fit" : "text");
        fontSize.setEnabled(!fitPreview.isSelected());
    }

    private void invalidatePreview() {
        updateOutputDimensions();
        generated = null;
        export.setEnabled(false);
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
        busy = value;
        choose.setEnabled(!busy);
        convert.setEnabled(!busy && source != null);
        export.setEnabled(!busy && generated != null);
        columns.setEnabled(!busy);
        format.setEnabled(!busy && source != null);
        contrast.setEnabled(!busy);
        clipping.setEnabled(!busy && contrast.isSelected());
        invert.setEnabled(!busy);
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser(new File("Images"));
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, BMP, GIF)",
                "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        setBusy(true);
        status.setText("Loading " + selected.getName() + "...");
        new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() throws Exception {
                BufferedImage image = ImageIO.read(selected);
                if (image == null) throw new IllegalArgumentException("This file is not a supported image.");
                return image;
            }

            protected void done() {
                boolean loaded = false;
                try {
                    source = get();
                    sourceFile = selected;
                    filename.setText(selected.getName());
                    filename.setToolTipText(selected.getAbsolutePath());
                    original.image = source;
                    original.repaint();
                    updateOutputDimensions();
                    generated = null;
                    preview.setText("");
                    fittedPreview.setAscii("");
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
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                return ImageInspect.toAscii(source, width, enhance, clip, reverse, selectedFormat);
            }

            protected void done() {
                try {
                    generated = get();
                    preview.setText(generated);
                    fittedPreview.setAscii(generated);
                    preview.setCaretPosition(0);
                    status.setText(source.getWidth() + " x " + source.getHeight() + " pixels  |  "
                            + generated.indexOf('\n') + " columns x " + generated.lines().count()
                            + " rows  |  GIF files use the first frame.");
                } catch (Exception exception) {
                    generated = null;
                    showError(exception);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void exportText() {
        if (generated == null || busy) return;
        try {
            Path folder = Path.of("ascii");
            Files.createDirectories(folder);
            String name = sourceFile.getName();
            int dot = name.lastIndexOf('.');
            Path output = folder.resolve((dot > 0 ? name.substring(0, dot) : name) + ".txt");
            Files.writeString(output, generated, StandardCharsets.UTF_8);
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

        FittedPreview() {
            setBackground(new Color(18, 22, 29));
            setForeground(new Color(232, 237, 244));
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        }

        void setAscii(String ascii) {
            rows = ascii.isEmpty() ? new String[0] : ascii.split("\n");
            columns = 0;
            for (String row : rows) columns = Math.max(columns, row.length());
            repaint();
        }

        // Fit the complete character grid, including whitespace padding, at a 1:2 cell ratio.
        Rectangle fittedBounds() {
            if (columns == 0 || rows.length == 0) return new Rectangle();
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
                    copy.drawString(rows[y], 0f, (float) (y * cellHeight + baseline));
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
