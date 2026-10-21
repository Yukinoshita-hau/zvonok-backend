package com.zvonok.service;

import com.zvonok.exception.InvalidMessageAttachmentException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Size, media allowlist and container signatures; does not replace a media decoder or malware scanner. */
final class StorageUploadValidation {
    private StorageUploadValidation() { }

    static String contentType(String value) {
        if (value == null) { throw invalid(); }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    static BufferedInputStream validate(InputStream input, long size, String declaredType) {
        String type = contentType(declaredType);
        long limit = type.startsWith("image/") ? 10L * 1024 * 1024
                : type.startsWith("audio/") ? 25L * 1024 * 1024 : 100L * 1024 * 1024;
        if (input == null || size <= 0 || size > limit) { throw invalid(); }
        BufferedInputStream stream = new BufferedInputStream(input);
        try {
            stream.mark(64);
            byte[] bytes = stream.readNBytes(32);
            stream.reset();
            boolean valid = switch (type) {
                case "application/pdf" -> text(bytes, 0, "%PDF-");
                case "application/zip", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                     "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                     "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> starts(bytes, 0, 80, 75, 3, 4) || starts(bytes, 0, 80, 75, 5, 6);
                case "text/plain", "text/markdown", "application/json", "text/csv" -> AttachmentFilePolicy.validUtf8(stream, size);
                case "image/png" -> starts(bytes, 0, 137, 80, 78, 71, 13, 10, 26, 10);
                case "image/jpeg" -> starts(bytes, 0, 255, 216, 255);
                case "image/gif" -> text(bytes, 0, "GIF87a") || text(bytes, 0, "GIF89a");
                case "image/webp" -> text(bytes, 0, "RIFF") && text(bytes, 8, "WEBP");
                case "video/webm", "audio/webm" -> starts(bytes, 0, 26, 69, 223, 163);
                case "video/mp4", "video/quicktime", "audio/mp4" -> text(bytes, 4, "ftyp");
                case "audio/ogg" -> text(bytes, 0, "OggS");
                case "audio/wav" -> text(bytes, 0, "RIFF") && text(bytes, 8, "WAVE");
                case "audio/mpeg" -> text(bytes, 0, "ID3") || (bytes.length >= 2
                        && (bytes[0] & 255) == 255 && (bytes[1] & 224) == 224);
                default -> false;
            };
            if (!valid || bytes.length > size || (size < 32 && bytes.length != size)) { throw invalid(); }
            return stream;
        } catch (IOException e) {
            throw invalid();
        }
    }

    private static boolean text(byte[] bytes, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) { return false; }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) { return false; }
        }
        return true;
    }

    private static boolean starts(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) { return false; }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 255) != expected[i]) { return false; }
        }
        return true;
    }

    private static InvalidMessageAttachmentException invalid() {
        return new InvalidMessageAttachmentException("Invalid media type, content or upload size");
    }
}
