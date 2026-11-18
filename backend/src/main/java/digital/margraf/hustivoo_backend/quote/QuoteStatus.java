package digital.margraf.hustivoo_backend.quote;

import java.util.Set;

/**
 * Quote lifecycle. Only drafts are editable or deletable; SENT is decided once by
 * ACCEPTED or REJECTED, both of which are final. The database trigger
 * {@code quotes_guard} (V6) enforces the same rules.
 */
public enum QuoteStatus {
    DRAFT, SENT, ACCEPTED, REJECTED;

    public Set<QuoteStatus> next() {
        return switch (this) {
            case DRAFT -> Set.of(SENT);
            case SENT -> Set.of(ACCEPTED, REJECTED);
            case ACCEPTED, REJECTED -> Set.of();
        };
    }

    public boolean canTransitionTo(QuoteStatus target) { return next().contains(target); }

    public boolean editable() { return this == DRAFT; }
}
