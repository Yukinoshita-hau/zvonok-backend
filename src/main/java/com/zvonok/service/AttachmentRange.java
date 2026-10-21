package com.zvonok.service;

/** One RFC 9110 byte range; multi-range is deliberately rejected. */
public record AttachmentRange(long start, long end) {
    public long length() { return end - start + 1; }
    public static AttachmentRange parse(String value, long size) {
        if (size <= 0) throw new IllegalArgumentException("Empty object");
        if (value == null) return new AttachmentRange(0, size - 1);
        if (!value.matches("bytes=\\d*-\\d*") || value.equals("bytes=-")) throw new IllegalArgumentException("Invalid Range");
        String[] parts = value.substring(6).split("-", -1);
        try {
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]);
                if (suffix <= 0) throw new IllegalArgumentException("Invalid suffix");
                return new AttachmentRange(Math.max(0, size - suffix), size - 1);
            }
            long start = Long.parseLong(parts[0]);
            long end = parts[1].isEmpty() ? size - 1 : Math.min(Long.parseLong(parts[1]), size - 1);
            if (start >= size || end < start) throw new IllegalArgumentException("Unsatisfiable Range");
            return new AttachmentRange(start, end);
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid Range", invalid); }
    }
}
