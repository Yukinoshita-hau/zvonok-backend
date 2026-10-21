package com.zvonok.service;

import com.zvonok.exception.InvalidMessageAttachmentException;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Upload permission never implies permission to execute or render active content. */
public final class AttachmentFilePolicy {
    private AttachmentFilePolicy() { }
    private static final Map<String, String> DOCUMENTS = Map.ofEntries(
        Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"),
        Map.entry("md", "text/markdown"), Map.entry("json", "application/json"),
        Map.entry("csv", "text/csv"), Map.entry("zip", "application/zip"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    private static final Map<String, String> MEDIA = Map.ofEntries(
        Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("mp4", "video/mp4"),
        Map.entry("webm", "video/webm"), Map.entry("mov", "video/quicktime"), Map.entry("mp3", "audio/mpeg"),
        Map.entry("ogg", "audio/ogg"), Map.entry("wav", "audio/wav"), Map.entry("m4a", "audio/mp4"));

    public static boolean isDocument(String type) { return DOCUMENTS.containsValue(type); }

    public static String contentType(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String expected = DOCUMENTS.getOrDefault(extension, MEDIA.get(extension));
        String declared = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean generic = declared.isEmpty() || declared.equals("application/octet-stream") || declared.equals("application/x-zip-compressed");
        if (expected == null || (!generic && !declared.equals(expected)
                && !(extension.equals("md") && declared.equals("text/plain"))
                && !(extension.equals("webm") && declared.equals("audio/webm")))) {
            throw new InvalidMessageAttachmentException("Unsupported extension or MIME mismatch");
        }
        return extension.equals("webm") && declared.equals("audio/webm") ? declared : expected;
    }

    static boolean validUtf8(BufferedInputStream stream, long size) throws IOException {
        if (size > 5L * 1024 * 1024) return false;
        stream.mark((int) size + 1);
        byte[] bytes = stream.readNBytes((int) size + 1);
        stream.reset();
        if (bytes.length != size) return false;
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException invalid) { return false; }
    }
}
