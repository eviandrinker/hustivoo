package digital.margraf.hustivoo_backend.business;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Validates and normalises uploaded logos with the JDK only. Accepted are PNG and JPEG, identified by
 * their file signature (the declared content type must agree). Dimensions are read from the header
 * before decoding, so oversized "decompression bombs" are rejected without allocating the image.
 * Every logo is re-encoded as PNG: this drops metadata (EXIF, GPS, comments), removes anything that is
 * not pixel data and guarantees that the PDF renderer can embed it. SVG is intentionally not supported.
 */
final class LogoImages {
    static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    static final int MAX_STORED_BYTES = 1024 * 1024;
    static final int MAX_SOURCE_PIXELS = 40_000_000;
    static final int MIN_EDGE = 16;
    static final int MAX_WIDTH = 1200;
    static final int MAX_HEIGHT = 600;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    record Normalized(byte[] png, int width, int height) {}

    static final class InvalidLogoException extends RuntimeException {
        InvalidLogoException(String message) { super(message); }
    }

    private LogoImages() {}

    static Normalized normalize(byte[] bytes, String declaredType) {
        if (bytes.length == 0) throw new InvalidLogoException("Logo is empty");
        if (bytes.length > MAX_UPLOAD_BYTES) throw new InvalidLogoException("Logo is too large");
        String format = startsWith(bytes, PNG) ? "png" : startsWith(bytes, JPEG) ? "jpeg" : null;
        String declared = declaredType == null ? "" : declaredType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (format == null || !declared.equals("image/" + format) && !(format.equals("jpeg") && declared.equals("image/jpg"))) {
            throw new InvalidLogoException("Unsupported logo type");
        }
        BufferedImage source = decode(bytes, format);
        int[] size = fit(source.getWidth(), source.getHeight());
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(size[0], size[1], alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, size[0], size[1], null);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(target, "png", out)) throw new InvalidLogoException("Logo could not be processed");
        } catch (IOException exception) { throw new InvalidLogoException("Logo could not be processed"); }
        if (out.size() > MAX_STORED_BYTES) throw new InvalidLogoException("Logo is too large");
        return new Normalized(out.toByteArray(), size[0], size[1]);
    }

    private static BufferedImage decode(byte[] bytes, String format) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (input == null || !readers.hasNext()) throw new InvalidLogoException("Unsupported logo type");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_EDGE || height < MIN_EDGE || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new InvalidLogoException("Unsupported logo dimensions");
                }
                return reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidLogoException invalid) throw invalid;
            throw new InvalidLogoException("Logo could not be read");
        }
    }

    /** Scales down (never up) into MAX_WIDTH × MAX_HEIGHT, keeping the aspect ratio. */
    static int[] fit(int width, int height) {
        double scale = Math.min(1, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
