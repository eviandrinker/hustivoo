package digital.margraf.hustivoo_backend.expense;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class FinanceService {
    final ExpenseRepository db; final ExpenseService expenses;
    public FinanceService(ExpenseRepository db,ExpenseService expenses) {this.db=db;this.expenses=expenses;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> dashboard(UUID business,LocalDate from,LocalDate to,String currency) {
        expenses.authorize(business);
        if(from==null||to==null||to.isBefore(from)||from.plusYears(5).isBefore(to)) throw new ExpenseValidationException("from","Bitte wähle einen Zeitraum von maximal fünf Jahren.");
        try {Currency.getInstance(currency);} catch(Exception e) {throw new ExpenseValidationException("currency","Bitte prüfe die Währung.");}
        Map<String,Object> totals=new LinkedHashMap<>();
        totals.put("invoiced",sum("select coalesce(sum(grand_total),0) from invoices where business_id=? and currency=? and issue_date between ? and ? and status not in ('DRAFT','CANCELLED')",business,currency,from,to));
        totals.put("outstanding",sum("select coalesce(sum(grand_total-paid_total),0) from invoices where business_id=? and currency=? and issue_date between ? and ? and status not in ('DRAFT','CANCELLED')",business,currency,from,to));
        totals.put("received",sum("select coalesce(sum(p.amount),0) from invoice_payments p join invoices i on i.id=p.invoice_id and i.business_id=p.business_id where p.business_id=? and i.currency=? and p.payment_date between ? and ? and p.reversed_at is null and i.status<>'CANCELLED'",business,currency,from,to));
        totals.put("expenses",sum("select coalesce(sum(gross_amount),0) from expenses where business_id=? and currency=? and expense_date between ? and ? and status<>'CANCELLED'",business,currency,from,to));
        totals.put("openExpenses",sum("select coalesce(sum(gross_amount),0) from expenses where business_id=? and currency=? and expense_date between ? and ? and status='UNPAID'",business,currency,from,to));
        totals.put("paidExpenses",sum("select coalesce(sum(amount),0) from expense_payments where business_id=? and currency=? and payment_date between ? and ? and reversed_at is null",business,currency,from,to));
        List<Map<String,Object>> months=new ArrayList<>();
        for(YearMonth month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)) {
            LocalDate start=month.atDay(1).isBefore(from)?from:month.atDay(1);LocalDate end=month.atEndOfMonth().isAfter(to)?to:month.atEndOfMonth();
            months.add(Map.of("month",month.toString(),"received",sum("select coalesce(sum(p.amount),0) from invoice_payments p join invoices i on i.id=p.invoice_id and i.business_id=p.business_id where p.business_id=? and i.currency=? and p.payment_date between ? and ? and p.reversed_at is null and i.status<>'CANCELLED'",business,currency,start,end),
                "paidExpenses",sum("select coalesce(sum(amount),0) from expense_payments where business_id=? and currency=? and payment_date between ? and ? and reversed_at is null",business,currency,start,end)));
        }
        return Map.of("businessId",business,"currency",currency,"from",from,"to",to,"totals",totals,"months",months);
    }
    private String sum(String sql,Object... args) {return db.sum(sql,args).setScale(2).toPlainString();}
}
