package com.zvonok.unittests.service;

import com.zvonok.exception.InvalidMessageAttachmentException;
import com.zvonok.exception.MessageNotFoundException;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomAttachmentLibraryTest {
    @Mock RoomService rooms;
    @Mock MessageAccessService access;
    @Mock MessageRepository messages;
    @Mock MessageAttachmentRepository attachments;
    @InjectMocks MessageService service;

    @Test void cursorBindsRoomFiltersDirectionAndPreservesTimestampPrecision() {
        var cursor = new AttachmentCursor(LocalDateTime.parse("2026-10-19T12:13:14.123456"), 42);
        String token = cursor.encode(7, "document", "документ\n100%_", "desc");
        assertEquals(cursor, AttachmentCursor.decode(token, 7, "document", "документ\n100%_", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(token, 8, "document", "документ\n100%_", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(token, 7, "audio", "документ\n100%_", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(token, 7, "document", "other", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(token, 7, "document", "документ\n100%_", "asc"));
        for (String invalid : List.of("!", "a".repeat(2049), "", "MTIz"))
            assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(invalid, 7, "all", "", "desc"));
        String outOfRange = new AttachmentCursor(LocalDateTime.of(10000, 1, 1, 0, 0), 1).encode(7, "all", "", "desc");
        assertThrows(InvalidMessageAttachmentException.class, () -> AttachmentCursor.decode(outOfRange, 7, "all", "", "desc"));
    }

    @Test void membershipIsCheckedBeforeAnyMetadataQueryIncludingNextPage() {
        when(rooms.getRoom(7L, "outsider")).thenThrow(new MessageNotFoundException("unavailable"));
        assertThrows(MessageNotFoundException.class, () -> service.attachments(7, "outsider", "all", null, 30, "secret", "desc"));
        verifyNoInteractions(attachments);
    }

    @Test void validatesBoundariesBeforeQuery() {
        for (int limit : new int[]{0, 61}) assertThrows(InvalidMessageAttachmentException.class,
                () -> service.attachments(7, "alice", "all", null, limit, "", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> service.attachments(7, "alice", "all", null, 30, "a".repeat(129), "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> service.attachments(7, "alice", "unknown", null, 30, "", "desc"));
        assertThrows(InvalidMessageAttachmentException.class, () -> service.attachments(7, "alice", "all", null, 30, "", "invalid"));
        verifyNoInteractions(attachments);
    }

    @Test void pageUsesLookaheadWithoutExposingStorageOrSignedUrls() {
        var user = new User(); user.setId(3L); user.setDisplayName("Alice");
        var message = new Message(); message.setId(11L); message.setSender(user); message.setSentAt(LocalDateTime.now());
        var a = new MessageAttachment(); a.setId(9L); a.setMessage(message); a.setType(AttachmentType.FILE);
        a.setOriginalFileName("C:\\private\\документ.pdf"); a.setStorageKey("SECRET"); a.setSizeBytes(10L);
        when(attachments.library(eq(7L), isNull(), eq(0L), eq(false), anySet(), eq("pdf"), any())).thenReturn(List.of(a, a));
        var page = service.attachments(7, "alice", "document", null, 1, " PDF ", "desc");
        assertEquals(1, page.items().size());
        assertEquals("документ.pdf", page.items().getFirst().attachment().originalFileName());
        assertNull(page.items().getFirst().attachment().url());
        assertNull(page.items().getFirst().attachment().downloadUrl());
        assertEquals(9, AttachmentCursor.decode(page.nextCursor(), 7, "document", "pdf", "desc").id());
    }

    @Test void contextRejectsMessageFromAnotherRoomAndDeletedOrUnreadableTargets() {
        var room = new Room(); room.setId(8L); var target = new Message(); target.setRoom(room);
        when(access.getReadableMessage(1L, "alice")).thenReturn(target);
        assertThrows(MessageNotFoundException.class, () -> service.messageContext(7, 1, "alice"));
        when(access.getReadableMessage(1L, "alice")).thenThrow(new MessageNotFoundException("gone"));
        assertThrows(MessageNotFoundException.class, () -> service.messageContext(7, 1, "alice"));
        verifyNoInteractions(messages);
    }
}
