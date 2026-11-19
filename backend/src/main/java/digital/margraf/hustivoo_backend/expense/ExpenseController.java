package digital.margraf.hustivoo_backend.expense;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.io.IOException;
import static digital.margraf.hustivoo_backend.expense.ExpenseDtos.*;

@RestController
@RequestMapping("/api/businesses/{businessId}")
public class ExpenseController {
    final ExpenseService service;final ReceiptService receipts;final FinanceService finance;
    public ExpenseController(ExpenseService service,ReceiptService receipts,FinanceService finance) {this.service=service;this.receipts=receipts;this.finance=finance;}
    @GetMapping("/suppliers") public Object suppliers(@PathVariable UUID businessId,@RequestParam(defaultValue="") @Size(max=320) String search,@RequestParam(defaultValue="false") boolean archived) {return service.suppliers(businessId,search,archived);}
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED) public Object supplier(@PathVariable UUID businessId,@Valid @RequestBody SupplierInput input) {return service.saveSupplier(businessId,null,input);}
    @PutMapping("/suppliers/{id}") public Object supplier(@PathVariable UUID businessId,@PathVariable UUID id,@Valid @RequestBody SupplierInput input) {return service.saveSupplier(businessId,id,input);}
    @PatchMapping("/suppliers/{id}/archive") public Object archiveSupplier(@PathVariable UUID businessId,@PathVariable UUID id) {return service.archiveReference(businessId,id,true,true);}
    @PatchMapping("/suppliers/{id}/restore") public Object restoreSupplier(@PathVariable UUID businessId,@PathVariable UUID id) {return service.archiveReference(businessId,id,true,false);}
    @GetMapping("/expense-categories") public Object categories(@PathVariable UUID businessId) {return service.categories(businessId);}
    @PostMapping("/expense-categories/defaults") public Object defaults(@PathVariable UUID businessId) {return service.defaults(businessId);}
    @PostMapping("/expense-categories") @ResponseStatus(HttpStatus.CREATED) public Object category(@PathVariable UUID businessId,@Valid @RequestBody CategoryInput input) {return service.saveCategory(businessId,null,input);}
    @PutMapping("/expense-categories/{id}") public Object category(@PathVariable UUID businessId,@PathVariable UUID id,@Valid @RequestBody CategoryInput input) {return service.saveCategory(businessId,id,input);}
    @PatchMapping("/expense-categories/{id}/archive") public Object archiveCategory(@PathVariable UUID businessId,@PathVariable UUID id) {return service.archiveReference(businessId,id,false,true);}
    @PatchMapping("/expense-categories/{id}/restore") public Object restoreCategory(@PathVariable UUID businessId,@PathVariable UUID id) {return service.archiveReference(businessId,id,false,false);}
    @GetMapping("/expenses") public Object list(@PathVariable UUID businessId,@RequestParam(defaultValue="") @Size(max=320) String search,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) UUID supplierId,@RequestParam(required=false) UUID categoryId,@RequestParam(required=false) Status status) {
        var rows=service.list(businessId,search,from,to,supplierId,categoryId,status);
        Map<String,java.math.BigDecimal> total=new TreeMap<>(),month=new TreeMap<>();
        for(var row:rows) if(!"CANCELLED".equals(row.get("status"))) {
            String currency=row.get("currency").toString();var amount=ExpenseService.amount(row,"grossAmount");total.merge(currency,amount,java.math.BigDecimal::add);
            if(java.time.YearMonth.from(LocalDate.parse(row.get("expenseDate").toString())).equals(java.time.YearMonth.from(ExpenseService.today()))) month.merge(currency,amount,java.math.BigDecimal::add);
        }
        Map<String,String> totals=new TreeMap<>(),months=new TreeMap<>();total.forEach((key,value)->totals.put(key,value.setScale(2).toPlainString()));month.forEach((key,value)->months.put(key,value.setScale(2).toPlainString()));
        return Map.of("items",rows,"totals",totals,"monthTotals",months,"businessId",businessId);
    }
    @PostMapping("/expenses/calculate") public Object calculate(@PathVariable UUID businessId,@Valid @RequestBody Input input) {service.authorize(businessId);return service.calculate(input);}
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED) public Object create(@PathVariable UUID businessId,@Valid @RequestBody Input input) {return service.save(businessId,null,input);}
    @GetMapping("/expenses/{id}") public Object get(@PathVariable UUID businessId,@PathVariable UUID id) {return service.get(businessId,id);}
    @PutMapping("/expenses/{id}") public Object update(@PathVariable UUID businessId,@PathVariable UUID id,@Valid @RequestBody Input input) {return service.save(businessId,id,input);}
    @DeleteMapping("/expenses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID businessId,@PathVariable UUID id) {service.delete(businessId,id);}
    @PatchMapping("/expenses/{id}/cancel") public Object cancel(@PathVariable UUID businessId,@PathVariable UUID id,@Valid @RequestBody Reason reason) {return service.cancel(businessId,id,reason);}
    @PostMapping("/expenses/{id}/payments") @ResponseStatus(HttpStatus.CREATED) public Object pay(@PathVariable UUID businessId,@PathVariable UUID id,@Valid @RequestBody PaymentInput input) {return service.pay(businessId,id,input);}
    @PatchMapping("/expenses/{id}/payments/{payment}/reverse") public Object reverse(@PathVariable UUID businessId,@PathVariable UUID id,@PathVariable UUID payment,@Valid @RequestBody Reason input) {return service.reverse(businessId,id,payment,input);}
    @GetMapping("/expenses/{id}/receipts") public Object receiptList(@PathVariable UUID businessId,@PathVariable UUID id) {return service.get(businessId,id).get("receipts");}
    @PostMapping(value="/expenses/{id}/receipts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Object upload(@PathVariable UUID businessId,@PathVariable UUID id,@RequestPart("file") MultipartFile file) throws IOException {return receipts.upload(businessId,id,file);}
    @GetMapping("/expenses/{id}/receipts/{receipt}") public ResponseEntity<byte[]> download(@PathVariable UUID businessId,@PathVariable UUID id,@PathVariable UUID receipt,@RequestParam(defaultValue="false") boolean download) throws IOException {
        var file=receipts.download(businessId,id,receipt);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.meta().get("contentType").toString())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'")
            .header(HttpHeaders.CONTENT_DISPOSITION,(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(file.meta().get("filename").toString(),StandardCharsets.UTF_8).build().toString()).body(file.bytes());
    }
    @DeleteMapping("/expenses/{id}/receipts/{receipt}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID businessId,@PathVariable UUID id,@PathVariable UUID receipt) {receipts.remove(businessId,id,receipt);}
    @GetMapping("/finance/overview") public Object dashboard(@PathVariable UUID businessId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="CHF") String currency) {return finance.dashboard(businessId,from,to,currency);}
}
