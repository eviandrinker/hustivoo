package digital.margraf.hustivoo_backend.expense;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.*;

@Service
public class ReceiptService {
    final ExpenseService expenses;final ExpenseRepository db;final ReceiptStorage storage;
    public ReceiptService(ExpenseService expenses,ExpenseRepository db,ReceiptStorage storage) {this.expenses=expenses;this.db=db;this.storage=storage;}
    @Transactional public Map<String,Object> upload(UUID business,UUID expense,MultipartFile file) throws IOException {
        expenses.authorize(business);var row=db.scoped("expenses",business,expense,true);ExpenseService.conflict("CANCELLED".equals(row.get("status")));
        if(file.getSize()>storage.maxBytes) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] raw;try(InputStream stream=file.getInputStream()) {raw=stream.readNBytes((int)Math.min(storage.maxBytes+1,Integer.MAX_VALUE));}
        var safe=storage.sanitize(raw);UUID id=UUID.randomUUID();
        String original=Optional.ofNullable(file.getOriginalFilename()).orElse("Beleg").replaceAll("[\\p{Cntrl}\\\\/\";]","_");
        String filename=original.substring(0,Math.min(original.length(),150));
        String extension=safe.type().equals("application/pdf")?".pdf":safe.type().equals("image/png")?".png":".jpg";
        if(!filename.toLowerCase(Locale.ROOT).endsWith(extension)) filename+=extension;
        storage.write(id,safe.bytes());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED) storage.remove(id);}
        });
        db.update("insert into expense_receipts(id,business_id,expense_id,filename,content_type,size_bytes) values(?,?,?,?,?,?)",id,business,expense,filename,safe.type(),safe.bytes().length);
        return db.scoped("expense_receipts",business,id,false);
    }
    public record Download(Map<String,Object> meta,byte[] bytes) {}
    @Transactional(readOnly=true) public Download download(UUID business,UUID expense,UUID receipt) throws IOException {
        expenses.authorize(business);db.scoped("expenses",business,expense,false);
        var row=db.scoped("expense_receipts",business,receipt,false);if(!expense.toString().equals(row.get("expenseId"))) throw ExpenseService.missing();
        return new Download(row,storage.read(receipt));
    }
    @Transactional public void remove(UUID business,UUID expense,UUID receipt) {
        expenses.authorize(business);var parent=db.scoped("expenses",business,expense,true);expenses.editable(business,expense,parent);
        var row=db.scoped("expense_receipts",business,receipt,true);if(!expense.toString().equals(row.get("expenseId"))) throw ExpenseService.missing();
        db.update("delete from expense_receipts where business_id=? and expense_id=? and id=?",business,expense,receipt);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() {storage.remove(receipt);} });
    }
}
