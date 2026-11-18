package digital.margraf.hustivoo_backend.document;

import digital.margraf.hustivoo_backend.document.DocumentFonts.Weight;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Thin drawing layer over PDFBox for A4 business documents: lazily created pages (so no empty page can
 * be produced), text with embedded fonts, word wrapping that never truncates, hairlines and images.
 * Coordinates are PDF points with the origin at the bottom left.
 */
public final class PdfCanvas implements AutoCloseable {
    public static final float WIDTH = PDRectangle.A4.getWidth();
    public static final float HEIGHT = PDRectangle.A4.getHeight();

    private final PDDocument document;
    private final DocumentFonts fonts;
    private PDPageContentStream stream;

    public PdfCanvas(PDDocument document, DocumentFonts fonts) {
        this.document = document;
        this.fonts = fonts;
    }

    public DocumentFonts fonts() { return fonts; }
    public int pageCount() { return document.getNumberOfPages(); }

    public void newPage() throws IOException {
        if (stream != null) stream.close();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
    }

    /** Appends to an existing page, e.g. for footers once the page count is known. */
    public void onPage(int index, Drawing drawing) throws IOException {
        PDPageContentStream previous = stream;
        try (PDPageContentStream append = new PDPageContentStream(document, document.getPage(index),
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream = append;
            drawing.draw();
        } finally {
            stream = previous;
        }
    }

    @FunctionalInterface
    public interface Drawing { void draw() throws IOException; }

    public void text(String value, Weight weight, float size, Color color, float x, float baseline) throws IOException {
        text(value, weight, size, color, x, baseline, 0);
    }

    public void text(String value, Weight weight, float size, Color color, float x, float baseline, float spacing) throws IOException {
        String cleaned = fonts.clean(value, false);
        if (cleaned.isBlank()) return;
        stream.beginText();
        stream.setFont(fonts.font(weight), size);
        stream.setNonStrokingColor(color);
        stream.setCharacterSpacing(spacing);
        stream.newLineAtOffset(x, baseline);
        stream.showText(cleaned);
        stream.setCharacterSpacing(0);
        stream.endText();
    }

    public void textRight(String value, Weight weight, float size, Color color, float right, float baseline) throws IOException {
        textRight(value, weight, size, color, right, baseline, 0);
    }

    public void textRight(String value, Weight weight, float size, Color color, float right, float baseline, float spacing) throws IOException {
        text(value, weight, size, color, right - width(value, weight, size, spacing), baseline, spacing);
    }

    public float width(String value, Weight weight, float size) { return width(value, weight, size, 0); }

    public float width(String value, Weight weight, float size, float spacing) {
        return fonts.width(fonts.clean(value, false), weight, size, spacing);
    }

    /**
     * Wraps text to the given width. Explicit line breaks are preserved (empty lines included) and words
     * longer than a line are split, so no content is ever cut off.
     */
    public List<String> wrap(String value, Weight weight, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : fonts.clean(value, true).split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split(" +")) {
                if (word.isEmpty()) continue;
                while (fonts.width(word, weight, size, 0) > maxWidth) {
                    if (!line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                    int cut = Math.max(longestPrefix(word, weight, size, maxWidth), word.offsetByCodePoints(0, 1));
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fonts.width(candidate, weight, size, 0) <= maxWidth) { line.setLength(0); line.append(candidate); }
                else { lines.add(line.toString()); line.setLength(0); line.append(word); }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private int longestPrefix(String word, Weight weight, float size, float maxWidth) {
        int end = 0;
        int next = 0;
        while (next < word.length()) {
            int candidate = word.offsetByCodePoints(next, 1);
            if (fonts.width(word.substring(0, candidate), weight, size, 0) > maxWidth) break;
            end = candidate;
            next = candidate;
        }
        return end;
    }

    /** Shortens a single-line text with an ellipsis (only for repeated running headers/footers). */
    public String fit(String value, Weight weight, float size, float maxWidth) {
        String cleaned = fonts.clean(value, false);
        if (fonts.width(cleaned, weight, size, 0) <= maxWidth) return cleaned;
        int end = longestPrefix(cleaned, weight, size, maxWidth - fonts.width("…", weight, size, 0));
        return cleaned.substring(0, end).stripTrailing() + "…";
    }

    public void rule(Color color, float from, float to, float y, float thickness) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(thickness);
        stream.moveTo(from, y);
        stream.lineTo(to, y);
        stream.stroke();
    }

    public void fill(Color color, float x, float y, float width, float height) throws IOException {
        stream.setNonStrokingColor(color);
        stream.addRect(x, y, width, height);
        stream.fill();
    }

    /** Rounded rectangle, filled and/or outlined. */
    public void roundedRect(float x, float y, float width, float height, float radius, Color fill, Color stroke, float lineWidth) throws IOException {
        float k = 0.5523f * radius;
        stream.moveTo(x + radius, y);
        stream.lineTo(x + width - radius, y);
        stream.curveTo(x + width - radius + k, y, x + width, y + radius - k, x + width, y + radius);
        stream.lineTo(x + width, y + height - radius);
        stream.curveTo(x + width, y + height - radius + k, x + width - radius + k, y + height, x + width - radius, y + height);
        stream.lineTo(x + radius, y + height);
        stream.curveTo(x + radius - k, y + height, x, y + height - radius + k, x, y + height - radius);
        stream.lineTo(x, y + radius);
        stream.curveTo(x, y + radius - k, x + radius - k, y, x + radius, y);
        stream.closePath();
        if (fill != null) stream.setNonStrokingColor(fill);
        if (stroke != null) { stream.setStrokingColor(stroke); stream.setLineWidth(lineWidth); }
        if (fill != null && stroke != null) stream.fillAndStroke(); else if (fill != null) stream.fill(); else stream.stroke();
    }

    /** Draws an image scaled into the box (top-left anchored); returns the drawn height. */
    public float image(byte[] bytes, float x, float top, float maxWidth, float maxHeight) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "logo");
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        stream.drawImage(image, x, top - height, width, height);
        return height;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) { stream.close(); stream = null; }
    }
}
