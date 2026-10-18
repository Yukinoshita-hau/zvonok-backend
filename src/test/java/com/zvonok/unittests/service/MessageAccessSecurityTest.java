package com.zvonok.unittests.service;

import com.zvonok.exception.InvalidMessageBatchException;
import com.zvonok.exception.MessageNotFoundException;
import com.zvonok.model.*;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageAccessSecurityTest {

	private final MessageRepository messages = mock(MessageRepository.class);
	private final RoomRepository rooms = mock(RoomRepository.class);
	private final UserService users = mock(UserService.class);
	private final MessageReadStatusRepository statuses = mock(MessageReadStatusRepository.class);
	private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
	private final MessageAccessService access = new MessageAccessService(messages,
			new RoomAccessService(rooms, users));
	private final MessageReadStatusService receipts = new MessageReadStatusService(statuses, broker, access, users);
	private User alice;
	private Room room;
	private Message message;

	@BeforeEach
	void setup() {
		alice = new User(); alice.setId(1L); alice.setUsername("alice");
		User bob = new User(); bob.setId(2L); bob.setUsername("bob");
		when(users.getUser("alice")).thenReturn(alice);
		when(users.getUser("bob")).thenReturn(bob);
		room = new Room(); room.setId(10L); room.setIsActive(true); room.setMembers(List.of(alice));
		when(rooms.findById(10L)).thenReturn(Optional.of(room));
		message = new Message(); message.setId(100L); message.setSender(alice); message.setRoom(room);
		when(messages.findById(100L)).thenReturn(Optional.of(message));
	}

	@Test void ownMessageIsReadable() { assertSame(message, access.getReadableMessage(100L, "alice")); }
	@Test void strangerIsDenied() { assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, "bob")); }
	@Test void removedMemberIsDeniedEvenForOwnMessage() {
		room.setMembers(List.of());
		assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, "alice"));
	}
	@Test void inactiveRoomIsDenied() {
		room.setIsActive(false);
		assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, "alice"));
	}
	@Test void deletedAndMissingMessagesHaveSameError() {
		message.setDeletedAt(LocalDateTime.now());
		assertEquals(assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(999L, "alice")).getMessage(),
				assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, "alice")).getMessage());
	}
	@Test void unauthenticatedReadDenied() { assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, null)); }
	@Test void archivedMessageWithoutRoomCannotBeReadOrMarked() {
		message.setRoom(null);
		assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(100L, "alice"));
		assertThrows(MessageNotFoundException.class, () -> receipts.markMessageAsRead(100L, "alice"));
		verifyNoInteractions(statuses, broker);
	}

	@Test void participantCanMarkAndDeduplicationSuppressesSecondEvent() {
		message.setSender(users.getUser("bob"));
		when(statuses.insertIngnore(eq(100L), eq(1L), any())).thenReturn(1, 0);
		receipts.markMessageAsRead(100L, "alice"); receipts.markMessageAsRead(100L, "alice");
		verify(broker, times(1)).convertAndSendToUser(eq("alice"), eq("/queue/message-read"), any(Object.class));
	}
	@Test void strangerCannotMarkEvenOwnMessageOutsideRoom() {
		message.setSender(users.getUser("bob"));
		assertThrows(MessageNotFoundException.class, () -> receipts.markMessageAsRead(100L, "bob"));
		verifyNoInteractions(statuses, broker);
	}
	@Test void strangerCannotReadReaders() {
		assertThrows(MessageNotFoundException.class, () -> receipts.getReaders(List.of(100L), "bob"));
		verifyNoInteractions(statuses);
	}
	@Test void mixedBatchDoesNotWriteOrPublish() {
		message.setSender(users.getUser("bob"));
		foreignMessage();
		assertThrows(MessageNotFoundException.class, () -> receipts.markMessagesAsRead(List.of(100L, 999L), "alice"));
		verifyNoInteractions(statuses, broker);
	}
	@Test void mixedReadersBatchDoesNotQueryReaders() {
		foreignMessage();
		assertThrows(MessageNotFoundException.class, () -> receipts.getReaders(List.of(100L, 999L), "alice"));
		verifyNoInteractions(statuses);
	}
	@Test void oversizedBatchRejectedBeforeLookup() {
		List<Long> ids = LongStream.range(1, 102).boxed().toList();
		assertThrows(InvalidMessageBatchException.class, () -> receipts.markMessagesAsRead(ids, "alice"));
		assertThrows(InvalidMessageBatchException.class, () -> receipts.getReaders(ids, "alice"));
		verifyNoInteractions(statuses, broker);
	}

	@Test void readersIncludesAllRows() {
		MessageReadStatus first = new MessageReadStatus(); first.setUser(alice);
		MessageReadStatus second = new MessageReadStatus(); second.setUser(users.getUser("bob"));
		when(statuses.findAllByMessageId(100L)).thenReturn(List.of(first, second));
		assertEquals(List.of("alice", "bob"), receipts.getReaders(List.of(100L), "alice").getFirst().getReaders());
	}

	private void foreignMessage() {
		Room foreign = new Room(); foreign.setId(11L); foreign.setIsActive(true);
		foreign.setMembers(List.of(users.getUser("bob")));
		Message secret = new Message(); secret.setId(999L); secret.setRoom(foreign);
		secret.setSender(users.getUser("bob"));
		when(messages.findById(999L)).thenReturn(Optional.of(secret));
		when(rooms.findById(11L)).thenReturn(Optional.of(foreign));
	}

}
