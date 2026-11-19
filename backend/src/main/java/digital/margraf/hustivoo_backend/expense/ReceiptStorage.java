package digital.margraf.hustivoo_backend.expense;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Component
@EnableScheduling
public class ReceiptStorage {
    final Path configured; final long maxBytes; private final ExpenseRepository db;
    public ReceiptStorage(@Value("${hustivoo.receipts.directory:${user.home}/.hustivoo/receipts}") String directory,
            @Value("${hustivoo.receipts.max-bytes:10485760}") long maxBytes,ExpenseRepository db) {
        this.configured=Path.of(directory).toAbsolutePath().normalize();this.maxBytes=maxBytes;this.db=db;
        for(Path p=Path.of("").toAbsolutePath();p!=null;p=p.getParent()) {
            if(Files.exists(p.resolve(".git")) && configured.startsWith(p)) throw new IllegalArgumentException("Receipt storage must be outside repository");
        }
    }
    Path root() throws IOException {
        if(!Files.exists(configured)) {
            try { Files.createDirectories(configured,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))); }
            catch(UnsupportedOperationException ignored) { Files.createDirectories(configured); }
        }
        if(Files.isSymbolicLink(configured)) throw new IOException("Storage root must not be a symlink");
        return configured.toRealPath();
    }
    Path path(UUID id) throws IOException { return root().resolve(id+".blob"); }
    public record SafeFile(byte[] bytes,String type) {}
    public SafeFile sanitize(byte[] bytes) {
        if(bytes.length==0 || bytes.length>maxBytes) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            boolean pdf=bytes.length>5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
            ByteArrayOutputStream output=new ByteArrayOutputStream();String type;
            if(pdf) {
                // Rasterise into a new PDF: no scripts, forms, links, attachments or original executable payload survive.
                try(PDDocument source=Loader.loadPDF(bytes); PDDocument clean=new PDDocument()) {
                    if(source.isEncrypted()||source.getNumberOfPages()<1||source.getNumberOfPages()>30) throw new IOException();
                    PDFRenderer renderer=new PDFRenderer(source);
                    for(int i=0;i<source.getNumberOfPages();i++) {
                        var box=source.getPage(i).getCropBox();float width=box.getWidth(),height=box.getHeight();
                        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0||width>2000||height>2000) throw new IOException();
                        float scale=Math.min(2f,2500f/Math.max(width,height));
                        BufferedImage rendered=renderer.renderImage(i,scale,org.apache.pdfbox.rendering.ImageType.RGB);
                        PDPage page=new PDPage(new PDRectangle(rendered.getWidth()/scale,rendered.getHeight()/scale)); clean.addPage(page);
                        try(PDPageContentStream content=new PDPageContentStream(clean,page)) {content.drawImage(JPEGFactory.createFromImage(clean,rendered,.9f),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}
                        rendered.flush();
                    }
                    clean.save(output);
                }
                type="application/pdf";
            } else {
                boolean png=bytes.length>8 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
                boolean jpeg=bytes.length>3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
                if(!png&&!jpeg) throw new IOException();
                try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext()) throw new IOException();var reader=readers.next();
                    try {reader.setInput(stream);long pixels=(long)reader.getWidth(0)*reader.getHeight(0);if(pixels<=0||pixels>20_000_000) throw new IOException();
                        BufferedImage image=reader.read(0);if(image==null) throw new IOException();ImageIO.write(image,png?"png":"jpeg",output);image.flush();
                    } finally {reader.dispose();}
                }
                type=png?"image/png":"image/jpeg";
            }
            if(output.size()==0 || output.size()>maxBytes) throw new IOException();return new SafeFile(output.toByteArray(),type);
        } catch(IOException|IllegalArgumentException exception) {throw new ExpenseValidationException("file","Bitte lade ein gültiges PDF (max. 30 Seiten), JPEG oder PNG bis 10 MB hoch.");}
    }
    void write(UUID id,byte[] data) throws IOException {
        Path temporary=root().resolve(id+".tmp");
        try {Files.write(temporary,data,StandardOpenOption.CREATE_NEW);Files.move(temporary,path(id),StandardCopyOption.ATOMIC_MOVE);}
        finally {Files.deleteIfExists(temporary);}
    }
    byte[] read(UUID id) throws IOException {Path file=path(id);if(Files.isSymbolicLink(file)) throw new IOException();return Files.readAllBytes(file);}
    void remove(UUID id) {try {Files.deleteIfExists(path(id));} catch(IOException ignored) { /* Retry through orphan cleanup. */ }}
    /** A one-day grace period prevents races with uncommitted uploads; never traverse or delete unrelated files. */
    @Scheduled(initialDelay=3600000,fixedDelay=3600000)
    public void cleanup() {
        if(!Files.isDirectory(configured)) return;
        try(var files=Files.list(root())) {
            Set<String> referenced=new HashSet<>();for(var row:db.query("select id from expense_receipts")) referenced.add(row.get("id").toString());
            for(Path file:files.toList()) {
                String name=file.getFileName().toString();
                if(!name.matches("[a-f0-9-]{36}\\.(blob|tmp)")||Files.isSymbolicLink(file)) continue;
                if(Files.getLastModifiedTime(file).toInstant().isBefore(Instant.now().minus(Duration.ofDays(1)))&&!referenced.contains(name.substring(0,36))) Files.deleteIfExists(file);
            }
        } catch(IOException|org.springframework.dao.DataAccessException ignored) { /* A failed scan never authorizes deletions. Retry next hour. */ }
    }
}
