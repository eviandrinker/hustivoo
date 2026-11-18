package digital.margraf.hustivoo_backend.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Embedded Inter (SIL Open Font License 1.1, bundled under {@code resources/fonts/inter}); nothing is
 * loaded from the network or the host system. Fonts are embedded as subsets, so PDFs stay small.
 *
 * <p>Fallback strategy for characters Inter cannot draw: NFC normalisation first; then canonical
 * decomposition (base letter + combining accent) if Inter has all parts; otherwise a visible "?"
 * is printed and the code point is recorded in {@link #missing()} so callers can log it. Inter covers
 * Latin incl. all European languages, Greek and Cyrillic; CJK, right-to-left scripts and emoji are not
 * supported.
 */
public final class DocumentFonts {
    public enum Weight { REGULAR, SEMIBOLD, BOLD }

    private static final Map<Weight, String> FILES = Map.of(
            Weight.REGULAR, "/fonts/inter/Inter-Regular.ttf",
            Weight.SEMIBOLD, "/fonts/inter/Inter-SemiBold.ttf",
            Weight.BOLD, "/fonts/inter/Inter-Bold.ttf");
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private final Map<Weight, PDType0Font> fonts = new EnumMap<>(Weight.class);
    private final Map<Integer, Boolean> supported = new HashMap<>();
    private final Set<Integer> missing = new TreeSet<>();

    private DocumentFonts() {}

    /** Loads the font set for one document (PDFBox font objects must not be shared across documents). */
    public static DocumentFonts load(PDDocument document) throws IOException {
        DocumentFonts set = new DocumentFonts();
        for (Weight weight : Weight.values()) {
            set.fonts.put(weight, PDType0Font.load(document, new ByteArrayInputStream(bytes(FILES.get(weight))), true));
        }
        return set;
    }

    private static byte[] bytes(String path) {
        return CACHE.computeIfAbsent(path, resource -> {
            try (InputStream in = DocumentFonts.class.getResourceAsStream(resource)) {
                if (in == null) throw new IllegalStateException("Missing bundled font " + resource);
                return in.readAllBytes();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public PDType0Font font(Weight weight) { return fonts.get(weight); }

    /** Code points that had to be replaced, for logging (never the surrounding text). */
    public Set<Integer> missing() { return Collections.unmodifiableSet(missing); }

    /**
     * Returns text that the fonts can draw. Tabs and other whitespace become spaces, control characters
     * are dropped; line breaks are kept only when {@code keepNewlines} is set.
     */
    public String clean(String value, boolean keepNewlines) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        Normalizer.normalize(value, Normalizer.Form.NFC).codePoints().forEach(codePoint -> {
            if (codePoint == '\r') return;
            if (codePoint == '\n') { result.append(keepNewlines ? "\n" : " "); return; }
            if (Character.isWhitespace(codePoint) || codePoint == 0x00A0) { result.append(' '); return; }
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) return;
            if (supports(codePoint)) { result.appendCodePoint(codePoint); return; }
            String decomposed = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFD);
            if (decomposed.codePointCount(0, decomposed.length()) > 1 && decomposed.codePoints().allMatch(this::supports)) {
                result.append(decomposed);
                return;
            }
            missing.add(codePoint);
            result.append('?');
        });
        return result.toString();
    }

    /** Width in points of already cleaned text; character spacing is applied between glyphs. */
    public float width(String cleaned, Weight weight, float size, float characterSpacing) {
        if (cleaned.isEmpty()) return 0;
        try {
            float width = fonts.get(weight).getStringWidth(cleaned) / 1000f * size;
            return width + characterSpacing * (cleaned.codePointCount(0, cleaned.length()) - 1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private boolean supports(int codePoint) {
        return supported.computeIfAbsent(codePoint, point -> {
            try {
                // All weights share the same character set; Regular is representative.
                fonts.get(Weight.REGULAR).encode(new String(Character.toChars(point)));
                return true;
            } catch (IllegalArgumentException | IOException exception) {
                return false;
            }
        });
    }
}
