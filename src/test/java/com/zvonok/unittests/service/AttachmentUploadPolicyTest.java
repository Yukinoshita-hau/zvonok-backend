package com.zvonok.unittests.service;

import com.zvonok.exception.InvalidMessageAttachmentException;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.MessageService;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import com.zvonok.service.RoomService;
import java.util.List;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentUploadPolicyTest {
    @Mock PlatformTransactionManager manager;
    @Mock RoomService rooms;
    @Mock S3Service storage;
    @Mock MessageRepository messages;
    @InjectMocks MessageService service;

    private MultipartFile video(long size) {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("video.mp4");
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.getSize()).thenReturn(size);
        return file;
    }

    @Test void rejectsVideoAboveServletCapBeforeAnyStorageWrite() {
        var file = video(50L * 1024 * 1024 + 1);
        assertThrows(InvalidMessageAttachmentException.class, () -> service.sendMessageWithAttachments(
                "alice", 1, "", List.of(file), null, null, null, null));
        verifyNoInteractions(storage); verify(messages, never()).save(any());
    }

    @Test void aggregateCapLeavesServletEnvelopeSpaceWithoutRaisingProductionLimits() {
        var file = video(26L * 1024 * 1024);
        assertThrows(InvalidMessageAttachmentException.class, () -> service.sendMessageWithAttachments(
                "alice", 1, "", List.of(file, file), null, null, null, null));
        verifyNoInteractions(storage); verify(messages, never()).save(any());
    }

    @Test void storageFailureCompensatesOnlyItsOwnKeyAndNeverCommitsMessage() throws Exception {
        var file = video(20);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[20]));
        doThrow(new IllegalStateException("controlled storage failure")).when(storage)
                .uploadFile(anyString(), any(), eq(20L), eq("video/mp4"));
        assertThrows(IllegalStateException.class, () -> service.sendMessageWithAttachments(
                "alice", 1, "", List.of(file), null, null, null, null));
        verify(storage).deleteFile(startsWith("message-attachments/"));
        verify(messages, never()).save(any());
    }
}
