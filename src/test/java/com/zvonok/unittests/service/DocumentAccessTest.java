package com.zvonok.unittests.service;

import com.zvonok.model.MessageAttachment;
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.service.AttachmentFilePolicy;
import com.zvonok.service.AttachmentMediaAccess;
import com.zvonok.service.MessageService;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAccessTest {
    @Test void downloadsUseTheStoredKeyAndCurrentAuthorizationForAllFileTypes() {
        MessageService messages = mock(MessageService.class); S3Service storage = mock(S3Service.class);
        var attachment = new MessageAttachment(); attachment.setStorageKey("private/zip"); attachment.setOriginalFileName("private.zip"); attachment.setSizeBytes(20L);
        when(messages.getAttachmentForDownload(7L, "alice")).thenReturn(attachment);
        when(storage.downloadUrl(eq("private/zip"), eq("private.zip"), any())).thenReturn("https://storage.invalid/download");
        var access = new AttachmentMediaAccess(messages, storage);
        assertEquals("application/octet-stream", access.issueDownload(7L, "alice").mimeType());
        when(messages.getAttachmentForDownload(7L, "alice")).thenThrow(new IllegalStateException("removed"));
        assertThrows(IllegalStateException.class, () -> access.issueDownload(7L, "alice"));
        verify(storage, times(1)).downloadUrl(anyString(), anyString(), any());
    }
    @Test void documentsAndImagesUseTheExistingAuthorizationAndScopedKey() {
        MessageService messages = mock(MessageService.class);
        S3Service storage = mock(S3Service.class);
        AttachmentMediaAccess access = new AttachmentMediaAccess(messages, storage);
        for (String mime : new String[]{"application/pdf", "text/plain", "text/markdown", "application/json", "text/csv", "image/png"}) {
            MessageAttachment attachment = new MessageAttachment();
            attachment.setType(mime.startsWith("image/") ? AttachmentType.IMAGE : AttachmentType.FILE);
            attachment.setContentType(mime); attachment.setStorageKey("private/7"); attachment.setSizeBytes(200L);
            when(messages.getAttachmentForDownload(7L, "alice")).thenReturn(attachment);
            when(storage.playbackUrl(eq("private/7"), eq(mime), any())).thenReturn("https://storage.invalid/scoped");
            assertEquals(mime, access.issuePlayback(7L, "alice").mimeType());
        }
        verify(messages, times(6)).getAttachmentForDownload(7L, "alice");
    }

    @Test void activeContentAndOfficeNeverReceiveInlineDirectAccess() {
        MessageService messages = mock(MessageService.class); S3Service storage = mock(S3Service.class);
        AttachmentMediaAccess access = new AttachmentMediaAccess(messages, storage);
        for (String mime : new String[]{"text/html", "image/svg+xml", "application/zip", "application/octet-stream"}) {
            MessageAttachment attachment = new MessageAttachment(); attachment.setType(AttachmentType.FILE); attachment.setContentType(mime);
            when(messages.getAttachmentForDownload(7L, "alice")).thenReturn(attachment);
            assertThrows(ResponseStatusException.class, () -> access.issuePlayback(7L, "alice"));
        }
        verifyNoInteractions(storage);
    }

    @Test void policyNormalizesDocumentsButRejectsMimeSpoofing() {
        for (String[] entry : new String[][]{{"file.json", "application/json"}, {"file.csv", "text/csv"}, {"file.pdf", "application/pdf"}}) {
            assertEquals(entry[1], AttachmentFilePolicy.contentType(new MockMultipartFile("files", entry[0], "application/octet-stream", new byte[]{1})));
        }
        assertThrows(RuntimeException.class, () -> AttachmentFilePolicy.contentType(new MockMultipartFile("files", "file.pdf", "text/html", new byte[]{1})));
        assertThrows(RuntimeException.class, () -> AttachmentFilePolicy.contentType(new MockMultipartFile("files", "file.svg", "image/svg+xml", new byte[]{1})));
    }
}
