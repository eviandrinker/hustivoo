package digital.margraf.hustivoo_backend.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Authoritative quote arithmetic. All values are BigDecimal; no floating point is involved.
 *
 * <ol>
 *   <li>lineSubtotal = quantity × unitPrice, rounded to the currency's minor unit (HALF_UP)</li>
 *   <li>lineTax = lineSubtotal × taxRate / 100, rounded the same way</li>
 *   <li>lineTotal = lineSubtotal + lineTax (exact)</li>
 *   <li>subtotal, taxTotal = exact sums of the rounded line values; grandTotal = subtotal + taxTotal</li>
 * </ol>
 * Rounding per line keeps every printed line consistent with the printed totals. The minor unit comes
 * from ISO 4217 (CHF/EUR/USD: 0.01, JPY: 1). No 5-Rappen cash rounding is applied. Stored values always
 * use scale 2, the column scale; currencies with more than two minor digits are not supported.
 */
public final class QuoteCalculator {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private QuoteCalculator() {}

    public record Line(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
    public record Totals(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal grandTotal) {}
    public record TaxGroup(BigDecimal rate, BigDecimal base, BigDecimal tax) {}

    public static boolean supported(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits >= 0 && digits <= 2;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    public static int minorDigits(String currency) {
        if (!supported(currency)) throw new IllegalArgumentException("Unsupported currency");
        return Currency.getInstance(currency).getDefaultFractionDigits();
    }

    public static Line line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate, String currency) {
        int scale = minorDigits(currency);
        BigDecimal subtotal = quantity.multiply(unitPrice).setScale(scale, RoundingMode.HALF_UP).setScale(2);
        BigDecimal tax = subtotal.multiply(taxRate).divide(HUNDRED).setScale(scale, RoundingMode.HALF_UP).setScale(2);
        return new Line(subtotal, tax, subtotal.add(tax));
    }

    public static Totals totals(List<Line> lines) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (Line line : lines) { subtotal = subtotal.add(line.subtotal()); tax = tax.add(line.tax()); }
        return new Totals(subtotal, tax, subtotal.add(tax));
    }

    /** Exact per-rate sums of the rounded line values, ascending by rate (for documents). */
    public static List<TaxGroup> taxGroups(List<QuoteItem> items) {
        Map<BigDecimal, BigDecimal[]> groups = new TreeMap<>();
        for (QuoteItem item : items) {
            BigDecimal[] sums = groups.computeIfAbsent(item.getTaxRate().setScale(2),
                    rate -> new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
            sums[0] = sums[0].add(item.getLineSubtotal());
            sums[1] = sums[1].add(item.getLineTax());
        }
        return groups.entrySet().stream().map(entry -> new TaxGroup(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();
    }

    public static boolean withinLimit(BigDecimal amount) { return amount.compareTo(MAX_AMOUNT) <= 0; }
}
