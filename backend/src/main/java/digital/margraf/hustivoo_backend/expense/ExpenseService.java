package digital.margraf.hustivoo_backend.expense;

import digital.margraf.hustivoo_backend.business.BusinessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import static digital.margraf.hustivoo_backend.expense.ExpenseDtos.*;

@Service
@Transactional(readOnly=true)
public class ExpenseService {
    final ExpenseRepository db; final BusinessService businesses;
    private final JsonMapper json=JsonMapper.builder().build();
    static final BigDecimal MAX=new BigDecimal("99999999999999999.99");
    static LocalDate today() { return LocalDate.now(ZoneId.of("Europe/Zurich")); }
    public ExpenseService(ExpenseRepository db,BusinessService businesses) { this.db=db;this.businesses=businesses; }
    UUID authorize(UUID business) { return businesses.access(business).userId(); }
    static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    static void conflict(boolean condition) { if(condition) throw new ResponseStatusException(HttpStatus.CONFLICT); }
    public List<Map<String,Object>> suppliers(UUID business,String search,boolean archived) {
        authorize(business);
        return db.query("select * from suppliers where business_id=? and archived=? and position(lower(?) in lower(name))>0 order by name,id",business,archived,search.strip());
    }
    @Transactional public Map<String,Object> saveSupplier(UUID business,UUID id,SupplierInput input) {
        authorize(business);
        if(!Arrays.asList(Locale.getISOCountries()).contains(input.country())) throw new ExpenseValidationException("country","Bitte prüfe das Land.");
        if(input.iban()!=null && !input.iban().isBlank() && !validIban(input.iban())) throw new ExpenseValidationException("iban","Bitte prüfe die IBAN.");
        if(id==null) { id=UUID.randomUUID(); db.update("insert into suppliers(id,business_id,name) values(?,?,?)",id,business,input.name()); }
        else db.scoped("suppliers",business,id,true);
        db.update("""
          update suppliers set name=?,contact_person=?,email=?,phone=?,street=?,house_number=?,postal_code=?,city=?,country=?,vat_number=?,iban=?,notes=?,updated_at=now()
          where business_id=? and id=?
          """,input.name(),input.contactPerson(),input.email(),input.phone(),input.street(),input.houseNumber(),input.postalCode(),input.city(),input.country(),input.vatNumber(),input.iban(),input.notes(),business,id);
        return db.scoped("suppliers",business,id,false);
    }
    private static boolean validIban(String value) {
        String rotated=value.substring(4)+value.substring(0,4); StringBuilder digits=new StringBuilder();
        for(char c:rotated.toCharArray()) digits.append(c>='A'&&c<='Z'?String.valueOf(c-'A'+10):String.valueOf(c));
        try { return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).equals(BigInteger.ONE); } catch(NumberFormatException e) {return false;}
    }
    @Transactional public Map<String,Object> archiveReference(UUID business,UUID id,boolean supplier,boolean archived) {
        authorize(business); String table=supplier?"suppliers":"expense_categories"; db.scoped(table,business,id,true);
        db.update("update "+table+" set archived=? where business_id=? and id=?",archived,business,id); return db.scoped(table,business,id,false);
    }
    public List<Map<String,Object>> categories(UUID business) { authorize(business); return db.query("select * from expense_categories where business_id=? order by name,id",business); }
    @Transactional public List<Map<String,Object>> defaults(UUID business) {
        authorize(business);
        for(String name:List.of("Software & Abonnements","Büro & Material","Marketing & Werbung","Reisen & Transport","Weiterbildung","Telekommunikation","Versicherungen","Miete & Infrastruktur","Externe Dienstleistungen","Sonstiges"))
            db.update("insert into expense_categories(id,business_id,name) values(?,?,?) on conflict do nothing",UUID.randomUUID(),business,name);
        return categories(business);
    }
    @Transactional public Map<String,Object> saveCategory(UUID business,UUID id,CategoryInput input) {
        authorize(business);
        if(id==null) {id=UUID.randomUUID();db.update("insert into expense_categories(id,business_id,name) values(?,?,?)",id,business,input.name());}
        else {db.scoped("expense_categories",business,id,true);db.update("update expense_categories set name=? where business_id=? and id=?",input.name(),business,id);}
        return db.scoped("expense_categories",business,id,false);
    }
    static BigDecimal amount(Map<String,Object> row,String key) { return new BigDecimal(row.get(key).toString()); }
    public Map<String,Object> calculate(Input input) {
        try { Currency.getInstance(input.currency()); } catch(Exception e) {throw new ExpenseValidationException("currency","Bitte prüfe die Währung.");}
        if(input.dueDate()!=null && input.dueDate().isBefore(input.expenseDate())) throw new ExpenseValidationException("dueDate","Fälligkeit darf nicht vor dem Ausgabedatum liegen.");
        BigDecimal net=input.netAmount().setScale(2);
        BigDecimal calculated=input.taxRate()==null?BigDecimal.ZERO.setScale(2):net.multiply(input.taxRate()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        BigDecimal tax=input.taxAmount()==null?calculated:input.taxAmount().setScale(2);
        // Permit up to CHF 0.05 (or equivalent currency units) receipt rounding, never an arbitrary tax override.
        if(tax.subtract(calculated).abs().compareTo(new BigDecimal("0.05"))>0 || input.taxRate()==null && tax.signum()!=0 || tax.compareTo(net)>0)
            throw new ExpenseValidationException("taxAmount","Steuerbetrag weicht um mehr als 0.05 vom berechneten Betrag ab oder ist unplausibel.");
        BigDecimal gross=net.add(tax);
        if(gross.compareTo(MAX)>0) throw new ExpenseValidationException("netAmount","Gesamtbetrag ist zu gross.");
        return Map.of("netAmount",net.toPlainString(),"taxAmount",tax.toPlainString(),"grossAmount",gross.toPlainString(),"calculatedTaxAmount",calculated.toPlainString());
    }
    public List<Map<String,Object>> list(UUID business,String search,LocalDate from,LocalDate to,UUID supplier,UUID category,Status status) {
        authorize(business);
        if(from!=null && to!=null && from.isAfter(to)) throw new ExpenseValidationException("from","Beginn liegt nach dem Ende.");
        String sql="select e.*,(select count(*) from expense_receipts r where r.business_id=e.business_id and r.expense_id=e.id) receipt_count from expenses e where e.business_id=?";
        List<Object> args=new ArrayList<>();args.add(business);
        if(!search.isBlank()) {sql+=" and (position(lower(?) in lower(e.description))>0 or position(lower(?) in lower(e.supplier_snapshot))>0 or position(lower(?) in lower(coalesce(e.reference,'')))>0)";Collections.addAll(args,search.strip(),search.strip(),search.strip());}
        if(from!=null) {sql+=" and e.expense_date>=?";args.add(from);} if(to!=null) {sql+=" and e.expense_date<=?";args.add(to);}
        if(supplier!=null) {sql+=" and e.supplier_id=?";args.add(supplier);} if(category!=null) {sql+=" and e.category_id=?";args.add(category);}
        if(status==Status.OVERDUE) {sql+=" and e.status='UNPAID' and e.due_date<? and e.gross_amount>0";args.add(today());}
        else if(status!=null) {sql+=" and e.status=?";args.add(status.name());}
        return db.query(sql+" order by e.expense_date desc,e.created_at desc,e.id",args.toArray()).stream().map(this::decorate).toList();
    }
    private Map<String,Object> decorate(Map<String,Object> row) {
        row.put("supplier",json.readValue(row.remove("supplierSnapshot").toString(),Map.class));
        boolean overdue="UNPAID".equals(row.get("status")) && row.get("dueDate")!=null && LocalDate.parse(row.get("dueDate").toString()).isBefore(today()) && amount(row,"grossAmount").signum()>0;
        row.put("displayStatus",overdue?"OVERDUE":row.get("status"));return row;
    }
    public Map<String,Object> get(UUID business,UUID id) {
        authorize(business);var row=decorate(db.scoped("expenses",business,id,false));
        row.put("payments",db.query("select * from expense_payments where business_id=? and expense_id=? order by recorded_at desc,id",business,id));
        row.put("receipts",db.query("select * from expense_receipts where business_id=? and expense_id=? order by created_at,id",business,id));return row;
    }
    void editable(UUID business,UUID id,Map<String,Object> row) {
        conflict(!"UNPAID".equals(row.get("status")) || !db.query("select id from expense_payments where business_id=? and expense_id=?",business,id).isEmpty());
    }
    @Transactional public Map<String,Object> save(UUID business,UUID id,Input input) {
        authorize(business);var amounts=calculate(input);Map<String,Object> old=null;
        if(id!=null) {old=db.scoped("expenses",business,id,true);editable(business,id,old);}
        var supplier=db.scoped("suppliers",business,input.supplierId(),true);var category=db.scoped("expense_categories",business,input.categoryId(),true);
        boolean sameSupplier=old!=null&&input.supplierId().toString().equals(old.get("supplierId"));
        boolean sameCategory=old!=null&&input.categoryId().toString().equals(old.get("categoryId"));
        if(!sameSupplier&&Boolean.TRUE.equals(supplier.get("archived"))) throw new ExpenseValidationException("supplierId","Lieferant ist archiviert.");
        if(!sameCategory&&Boolean.TRUE.equals(category.get("archived"))) throw new ExpenseValidationException("categoryId","Kategorie ist archiviert.");
        String snapshot=sameSupplier?old.get("supplierSnapshot").toString():json.writeValueAsString(supplier);
        Object categoryName=sameCategory?old.get("categoryName"):category.get("name");
        if(id==null) {
            id=UUID.randomUUID();db.update("""
              insert into expenses(id,business_id,supplier_id,category_id,supplier_snapshot,category_name,description,reference,expense_date,due_date,currency,net_amount,tax_rate,tax_amount,gross_amount,input_tax,notes)
              values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
              """,id,business,input.supplierId(),input.categoryId(),snapshot,categoryName,input.description(),input.reference(),input.expenseDate(),input.dueDate(),input.currency(),amount(amounts,"netAmount"),input.taxRate(),amount(amounts,"taxAmount"),amount(amounts,"grossAmount"),input.inputTax().name(),input.notes());
        } else db.update("""
            update expenses set supplier_id=?,category_id=?,supplier_snapshot=?,category_name=?,description=?,reference=?,expense_date=?,due_date=?,currency=?,net_amount=?,tax_rate=?,tax_amount=?,gross_amount=?,input_tax=?,notes=?,updated_at=now() where business_id=? and id=?
            """,input.supplierId(),input.categoryId(),snapshot,categoryName,input.description(),input.reference(),input.expenseDate(),input.dueDate(),input.currency(),amount(amounts,"netAmount"),input.taxRate(),amount(amounts,"taxAmount"),amount(amounts,"grossAmount"),input.inputTax().name(),input.notes(),business,id);
        return get(business,id);
    }
    @Transactional public Map<String,Object> pay(UUID business,UUID id,PaymentInput input) {
        UUID user=authorize(business);var row=db.scoped("expenses",business,id,true);conflict(!"UNPAID".equals(row.get("status")));
        if(input.paymentDate().isAfter(today())) throw new ExpenseValidationException("paymentDate","Zahlungsdatum liegt in der Zukunft.");
        db.update("insert into expense_payments(id,business_id,expense_id,payment_date,method,amount,currency,recorded_by) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),business,id,input.paymentDate(),input.method().name(),amount(row,"grossAmount"),row.get("currency"),user);
        db.update("update expenses set status='PAID',updated_at=now() where business_id=? and id=?",business,id);return get(business,id);
    }
    @Transactional public Map<String,Object> reverse(UUID business,UUID id,UUID payment,Reason reason) {
        UUID user=authorize(business);var row=db.scoped("expenses",business,id,true);
        var booking=db.query("select * from expense_payments where business_id=? and expense_id=? and id=?",business,id,payment).stream().findFirst().orElseThrow(ExpenseService::missing);
        conflict(!"PAID".equals(row.get("status")) || booking.get("reversedAt")!=null);
        db.update("update expense_payments set reversed_at=now(),reversed_by=?,reversal_reason=? where business_id=? and expense_id=? and id=?",user,reason.reason(),business,id,payment);
        db.update("update expenses set status='UNPAID',updated_at=now() where business_id=? and id=?",business,id);return get(business,id);
    }
    @Transactional public Map<String,Object> cancel(UUID business,UUID id,Reason reason) {
        authorize(business);var row=db.scoped("expenses",business,id,true);conflict(!"UNPAID".equals(row.get("status")));
        db.update("update expenses set status='CANCELLED',cancellation_reason=?,updated_at=now() where business_id=? and id=?",reason.reason(),business,id);return get(business,id);
    }
    @Transactional public void delete(UUID business,UUID id) {
        authorize(business);editable(business,id,db.scoped("expenses",business,id,true));
        conflict(!db.query("select id from expense_receipts where business_id=? and expense_id=?",business,id).isEmpty());
        db.update("delete from expenses where business_id=? and id=?",business,id);
    }
}
