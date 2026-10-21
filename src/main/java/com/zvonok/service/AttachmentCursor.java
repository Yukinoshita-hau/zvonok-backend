package com.zvonok.service;

import com.zvonok.exception.InvalidMessageAttachmentException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** A position, never an authorization token; every page checks current room membership. */
public record AttachmentCursor(LocalDateTime sentAt, long id) {
    public String encode(long roomId, String category, String query, String direction) {
        String scope = scope(roomId, category, query, direction);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (scope + "\n" + sentAt + "\n" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static AttachmentCursor decode(String value, long roomId, String category, String query, String direction) {
        if (value == null) return null;
        try {
            if (value.length() > 2048) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\n", -1);
            if (parts.length != 3 || !parts[0].equals(scope(roomId, category, query, direction))) throw new IllegalArgumentException();
            var cursor = new AttachmentCursor(LocalDateTime.parse(parts[1]), Long.parseLong(parts[2]));
            if (cursor.id <= 0 || cursor.sentAt.getYear() < 1 || cursor.sentAt.getYear() > 9999)
                throw new IllegalArgumentException();
            return cursor;
        } catch (RuntimeException e) {
            throw new InvalidMessageAttachmentException("Invalid attachment cursor");
        }
    }

    private static String scope(long roomId, String category, String query, String direction) {
        return roomId + ":" + category + ":" + direction + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(query.getBytes(StandardCharsets.UTF_8));
    }
}
