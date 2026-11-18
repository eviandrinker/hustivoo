package digital.margraf.hustivoo_backend.document;

import java.awt.Color;
import java.util.Objects;

/**
 * Visual identity of generated business documents. Today only the Hustivoo default exists; a later
 * branding setting can supply its own primary/secondary colour and footer note without touching layouts.
 *
 * @param primary    accent for the document label, total rule and brand mark (dark green by default)
 * @param secondary  subtle tint for highlights such as the draft marker background
 * @param text       body text
 * @param muted      labels, secondary information, footer
 * @param rule       hairlines between rows and around the footer
 * @param warning    draft marker
 * @param footerNote optional extra footer line (e.g. bank details); null when not configured
 */
public record DocumentTheme(Color primary, Color secondary, Color text, Color muted, Color rule, Color warning, String footerNote) {
    public DocumentTheme {
        Objects.requireNonNull(primary); Objects.requireNonNull(secondary); Objects.requireNonNull(text);
        Objects.requireNonNull(muted); Objects.requireNonNull(rule); Objects.requireNonNull(warning);
        footerNote = footerNote == null || footerNote.isBlank() ? null : footerNote.strip();
    }

    public static DocumentTheme hustivoo() {
        return new DocumentTheme(new Color(0x245747), new Color(0xEAF2ED), new Color(0x1B2420),
                new Color(0x5F675F), new Color(0xDFE4DF), new Color(0x805E27), null);
    }

    public DocumentTheme withColors(Color primary, Color secondary) {
        return new DocumentTheme(primary, secondary, text, muted, rule, warning, footerNote);
    }

    public DocumentTheme withFooterNote(String note) {
        return new DocumentTheme(primary, secondary, text, muted, rule, warning, note);
    }
}
