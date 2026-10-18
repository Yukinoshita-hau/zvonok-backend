package com.zvonok.unittests.service;

import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.MessageAccessService;
import com.zvonok.service.MessageService;
import com.zvonok.service.RoomReadStateService;
import com.zvonok.service.RoomService;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled transaction outcome fixture; real persistence failures are tested separately in PostgreSQL IT. */
class AttachmentTransactionOutcomeTest {
    @Test void unknownCommitOutcomeRetainsObjectsInsteadOfDeletingPossiblyCommittedReferences() {
        var repository = mock(MessageRepository.class);
        var rooms = mock(RoomService.class); var users = mock(UserService.class);
        var storage = mock(S3Service.class); var broker = mock(SimpMessagingTemplate.class);
        User user = new User(); user.setId(1L); user.setUsername("alice");
        Room room = new Room(); room.setId(2L); room.setType(RoomType.GROUP); room.setMembers(List.of(user));
        when(rooms.getRoom(2L, "alice")).thenReturn(room);
        when(rooms.getRoomForUpdate(2L, "alice")).thenReturn(room);
        when(users.getUser("alice")).thenReturn(user);
        when(repository.save(any())).thenAnswer(i -> { Message message = i.getArgument(0); message.setId(3L); return message; });
        var manager = new AbstractPlatformTransactionManager() {
            int commits;
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) {}
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (++commits == 3) throw new TransactionSystemException("commit outcome unknown");
            }
            @Override protected void doRollback(DefaultTransactionStatus status) {}
        };
        var service = new MessageService(repository, mock(MessageAccessService.class), broker, rooms, users,
                mock(MessageAttachmentRepository.class),
                storage, mock(RoomReadStateService.class), manager);
        var file = new MockMultipartFile("files", "voice", "audio/webm", new byte[]{1, 2, 3});
        assertEquals("commit outcome unknown", assertThrows(TransactionSystemException.class,
                () -> service.sendMessageWithAttachments("alice", 2L, "", List.of(file), AttachmentType.AUDIO, 1000L, null, null)).getMessage());
        verify(storage).uploadFile(startsWith("message-attachments/"), any(), eq(3L), eq("audio/webm"));
        verify(storage, never()).deleteFile(anyString()); verifyNoInteractions(broker);
    }
}
