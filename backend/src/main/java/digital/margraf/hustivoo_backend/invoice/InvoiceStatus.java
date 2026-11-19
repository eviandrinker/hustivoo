package digital.margraf.hustivoo_backend.invoice;

/**
 * Stored invoice lifecycle (see V8 {@code invoices_guard}):
 * <pre>
 * DRAFT ──issue──▶ ISSUED ◀──payments──▶ PARTIALLY_PAID ◀──payments──▶ PAID
 *                    │
 *                    └──cancel (no active payments)──▶ CANCELLED (final)
 * </pre>
 * The payment states follow the booked payments only; they are never set directly. OVERDUE is not a
 * stored state but a display state ({@link #display}), so accounting data is never altered by time.
 */
public enum InvoiceStatus {
    DRAFT, ISSUED, PARTIALLY_PAID, PAID, CANCELLED;

    /** Filter and badge values: the stored states plus the derived OVERDUE. */
    public enum Display { DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }

    public boolean open() { return this == ISSUED || this == PARTIALLY_PAID; }

    public Display display(boolean overdue) {
        return overdue ? Display.OVERDUE : Display.valueOf(name());
    }
}
