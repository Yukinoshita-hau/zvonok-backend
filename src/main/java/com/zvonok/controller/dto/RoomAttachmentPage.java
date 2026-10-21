package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RoomAttachmentPage(List<Item> items, String nextCursor) {
    public record Item(MessageAttachmentDto attachment, Long messageId, LocalDateTime sentAt,
                       Long authorId, String authorName) { }
}
