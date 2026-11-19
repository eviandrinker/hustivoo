package digital.margraf.hustivoo_backend.expense;
import java.util.Map;
public class ExpenseValidationException extends RuntimeException {
    final Map<String,String> fields;
    public ExpenseValidationException(String field,String message) { super("Invalid request"); fields=Map.of(field,message); }
}
