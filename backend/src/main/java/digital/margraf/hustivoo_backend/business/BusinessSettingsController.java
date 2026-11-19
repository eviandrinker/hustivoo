package digital.margraf.hustivoo_backend.business;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/settings")
public class BusinessSettingsController {
    private final BusinessSettingsService settings;

    public BusinessSettingsController(BusinessSettingsService settings) { this.settings = settings; }

    @GetMapping
    public BusinessSettingsResponse get(@PathVariable UUID businessId) { return settings.get(businessId); }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BusinessSettingsResponse update(@PathVariable UUID businessId, @Valid @RequestBody BusinessSettingsRequest request) {
        return settings.update(businessId, request);
    }

    /**
     * The logo is sent as the raw request body (Content-Type image/png or image/jpeg). The type is checked
     * against the file signature in {@link LogoImages}; the body is read with a hard limit, so oversized
     * uploads are rejected without buffering them.
     */
    @PutMapping("/logo")
    public BusinessSettingsResponse uploadLogo(@PathVariable UUID businessId, HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > LogoImages.MAX_UPLOAD_BYTES) throw new LogoImages.InvalidLogoException("Logo is too large");
        byte[] content;
        try (InputStream input = request.getInputStream()) { content = input.readNBytes(LogoImages.MAX_UPLOAD_BYTES + 1); }
        return settings.replaceLogo(businessId, content, request.getContentType());
    }

    @DeleteMapping("/logo")
    public BusinessSettingsResponse removeLogo(@PathVariable UUID businessId) { return settings.removeLogo(businessId); }

    /** Private, revalidated on every use (ETag = logo ID), so a cached logo never outlives the membership check. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID businessId) {
        BusinessLogo logo = settings.logo(businessId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag("\"" + logo.getId() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("logo.png").build().toString())
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(logo.getContent());
    }
}
