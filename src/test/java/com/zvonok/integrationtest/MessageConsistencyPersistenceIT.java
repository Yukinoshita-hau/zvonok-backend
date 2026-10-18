package com.zvonok.integrationtest;

import com.zvonok.controller.dto.ShortMessageWrapped;
import com.zvonok.model.Message;
import com.zvonok.model.MessageAttachment;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.repository.RoomReadStateRepository;
import com.zvonok.service.FriendShipAccessService;
import com.zvonok.service.MessageAccessService;
import com.zvonok.service.MessageService;
import com.zvonok.service.RoomAccessService;
import com.zvonok.service.RoomReadStateService;
import com.zvonok.service.RoomService;
import com.zvonok.service.RoomInviteService;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserService;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true", "spring.jpa.show-sql=false"})
@ContextConfiguration(classes = MessageConsistencyPersistenceIT.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageConsistencyPersistenceIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Configuration
    @EntityScan(basePackageClasses = Message.class)
    @EnableJpaRepositories(basePackageClasses = MessageRepository.class)
    @Import({MessageService.class, RoomService.class, RoomAccessService.class, RoomReadStateService.class,
            RoomInviteService.class})
    static class Config {}
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired MessageService service;
    @Autowired MessageRepository messages;
    @Autowired MessageAttachmentRepository attachments;
    @Autowired RoomRepository rooms;
    @Autowired RoomReadStateRepository readStates;
    @Autowired RoomReadStateService reads;
    @Autowired RoomService roomService;
    @Autowired RoomInviteService invites;
    @MockitoBean UserService users;
    @MockitoBean MessageAccessService access;
    @MockitoBean FriendShipAccessService friends;
    @MockitoBean S3Service storage;
    @MockitoBean SimpMessagingTemplate broker;
    TransactionTemplate tx;
    User alice, bob;
    Room room;
    List<String> uploaded;
    List<String> removed;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        uploaded = new ArrayList<>(); removed = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            alice = user(); bob = user();
            room = new Room(); room.setType(RoomType.GROUP); room.setIsActive(true);
            room.setMembers(new ArrayList<>(List.of(alice, bob))); em.persist(room); em.flush();
        });
        when(users.getUser(alice.getUsername())).thenReturn(alice);
        when(users.getUser(bob.getUsername())).thenReturn(bob);
        when(users.getUser(alice.getId())).thenReturn(alice);
        when(users.getUser(bob.getId())).thenReturn(bob);
        doAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "S3 upload must not hold DB transaction");
            uploaded.add(i.getArgument(0)); return null;
        }).when(storage).uploadFile(anyString(), any(), anyLong(), anyString());
        doAnswer(i -> { removed.add(i.getArgument(0)); return null; }).when(storage).deleteFile(anyString());
    }
    User user() {
        User u = new User(); u.setUsername("u" + UUID.randomUUID().toString().substring(0, 8));
        u.setDisplayName("Test"); u.setEmail(u.getUsername() + "@test.invalid"); u.setPassword("test-only"); em.persist(u); return u;
    }
    ShortMessageWrapped send(String content) { return service.sendMessage(alice.getUsername(), room.getId(), content, null); }
    List<ShortMessageWrapped> history() { return service.getRoomMessages(alice.getUsername(), room.getId(), null, 100); }
    MockMultipartFile file(String name) { return new MockMultipartFile("files", name, "audio/webm", new byte[]{1,2,3}); }

    @Test void privateRoomCreationRemainsReusableWithoutLegacyModels() {
        Room first = tx.execute(status -> roomService.createOrGetPrivateRoom(alice.getUsername(), bob.getUsername()));
        Room again = tx.execute(status -> roomService.createOrGetPrivateRoom(bob.getUsername(), alice.getUsername()));
        assertNotNull(first); assertNotNull(again);
        assertEquals(RoomType.PRIVATE, first.getType()); assertEquals(first.getId(), again.getId());
        assertEquals(2, tx.execute(status -> em.find(Room.class, first.getId()).getMembers().size()).intValue());
    }

    @Test void groupCreationAddAndLeaveKeepMembershipChecks() {
        when(friends.areFriends(alice.getId(), bob.getId())).thenReturn(true);
        Room group = roomService.createGroupRoom(alice.getUsername(), "retained group", List.of());
        when(users.getUsersByIds(any())).thenReturn(List.of(bob));
        assertThrows(com.zvonok.exception.UserNotMemberRoomException.class,
                () -> roomService.addMembers(group.getId(), bob.getUsername(), List.of(alice.getId())));
        assertEquals(1, roomService.addMembers(group.getId(), alice.getUsername(), List.of(bob.getId())).addedMembers().size());
        assertNotNull(service.sendMessage(bob.getUsername(), group.getId(), "joined", null));
        roomService.leaveRoom(bob.getUsername(), group.getId());
        assertThrows(com.zvonok.exception.UserNotMemberRoomException.class,
                () -> service.sendMessage(bob.getUsername(), group.getId(), "removed", null));
    }

    @Test void groupInvitationJoinAndDuplicateRemainIndependentOfServerInvites() {
        Room group = roomService.createGroupRoom(alice.getUsername(), "invited group", List.of());
        assertThrows(com.zvonok.exception.UserNotMemberRoomException.class,
                () -> invites.createInvite(group.getId(), bob.getUsername(), null));
        var invite = invites.createInvite(group.getId(), alice.getUsername(), null);
        assertTrue(invites.join(invite.inviteToken(), bob.getUsername()).joined());
        assertTrue(invites.join(invite.inviteToken(), bob.getUsername()).alreadyMember());
        assertNotNull(service.sendMessage(bob.getUsername(), group.getId(), "invited", null));
        assertThrows(com.zvonok.exception.RoomInviteNotFoundException.class,
                () -> invites.join("retired-server-invite", bob.getUsername()));
    }
    @Test void invitationRollbackDoesNotAnnounceMembershipAndCommitAnnouncesOnce() {
        Room group = roomService.createGroupRoom(alice.getUsername(), "stage10 invite", List.of());
        var invite = invites.createInvite(group.getId(), alice.getUsername(), null);
        clearInvocations(broker);
        tx.executeWithoutResult(status -> {
            assertTrue(invites.join(invite.inviteToken(), bob.getUsername()).joined());
            status.setRollbackOnly();
        });
        assertEquals(1, tx.execute(status -> em.find(Room.class, group.getId()).getMembers().size()).intValue());
        verifyNoInteractions(broker);
        tx.executeWithoutResult(status -> {
            assertTrue(invites.join(invite.inviteToken(), bob.getUsername()).joined());
            verifyNoInteractions(broker);
        });
        assertEquals(2, tx.execute(status -> em.find(Room.class, group.getId()).getMembers().size()).intValue());
        verify(broker, times(2)).convertAndSendToUser(anyString(), eq("/queue/room-events"), any(Object.class));
        clearInvocations(broker);
        assertTrue(invites.join(invite.inviteToken(), bob.getUsername()).alreadyMember());
        verifyNoInteractions(broker);
    }
    ShortMessageWrapped upload(int count) {
        return service.sendMessageWithAttachments(alice.getUsername(), room.getId(), "", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> (org.springframework.web.multipart.MultipartFile)file("voice" + i)).toList(), AttachmentType.AUDIO, 1000L, null, null);
    }
    @Test void commitPublishesAfterPersistenceAndRollbackPublishesNothing() {
        tx.executeWithoutResult(status -> {
            send("rollback"); verifyNoInteractions(broker); status.setRollbackOnly();
        });
        assertTrue(history().isEmpty()); assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        tx.executeWithoutResult(status -> { send("commit"); verifyNoInteractions(broker); });
        assertEquals("commit", history().getFirst().getContent());
        verify(broker, times(2)).convertAndSendToUser(anyString(), eq("/queue/messages"), any(Object.class));
    }
    @Test void failedBrokerRetainsCommittedMessageAndAttemptsOtherRecipient() {
        doThrow(new IllegalStateException("broker down")).when(broker).convertAndSendToUser(eq(alice.getUsername()), anyString(), any(Object.class));
        var sent = assertDoesNotThrow(() -> send("durable"));
        assertTrue(messages.existsById(sent.getId())); assertEquals(sent.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        verify(broker).convertAndSendToUser(eq(bob.getUsername()), eq("/queue/messages"), any(Object.class));
    }
    @ParameterizedTest @EnumSource(RoomType.class)
    void editDeleteAndSendMaintainPreviewAndUnread(RoomType type) {
        tx.executeWithoutResult(s -> em.find(Room.class, room.getId()).setType(type));
        var old = send("old"); var latest = send("latest");
        service.editMessage(old.getId(), alice.getUsername(), "edited old");
        assertEquals(latest.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        assertEquals("latest", rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        service.editMessage(latest.getId(), alice.getUsername(), "edited last");
        assertEquals("edited last", rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        assertEquals(2, reads.getUnreadCountsForRooms(bob.getId(), List.of(room.getId())).get(room.getId()));
        service.deleteMessage(latest.getId(), alice.getUsername());
        assertEquals(old.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        assertEquals("edited old", rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        assertEquals(1, history().size()); assertEquals("edited old", history().getFirst().getContent());
        assertEquals(1, reads.getUnreadCountsForRooms(bob.getId(), List.of(room.getId())).get(room.getId()));
        service.deleteMessage(old.getId(), alice.getUsername());
        assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        var next = send("after deletion"); assertEquals(next.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
    }
    @Test void clearResetsHistoryPreviewAndUnreadAndAllowsNewSend() {
        send("first"); send("second"); reads.markRoomAsRead(bob.getUsername(), room.getId());
        service.clearRoomMessages(room.getId(), alice.getUsername());
        assertTrue(history().isEmpty()); assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        assertEquals(0, reads.getUnreadCountsForRooms(bob.getId(), List.of(room.getId())).get(room.getId()));
        var next = send("after clear"); assertEquals(next.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        assertEquals(List.of(next.getId()), history().stream().map(ShortMessageWrapped::getId).toList());
        assertEquals(1, reads.getUnreadCountsForRooms(bob.getId(), List.of(room.getId())).get(room.getId()));
    }
    @Test void rollbackEditDeleteAndClearPreservesHistoryAndPreview() {
        var a = send("a"); var b = send("b"); clearInvocations(broker);
        tx.executeWithoutResult(s -> { service.editMessage(a.getId(), alice.getUsername(), "no");
            service.deleteMessage(b.getId(), alice.getUsername()); service.clearRoomMessages(room.getId(), alice.getUsername());
            verifyNoInteractions(broker); s.setRollbackOnly(); });
        assertEquals(List.of("a", "b"), history().stream().map(ShortMessageWrapped::getContent).toList());
        assertEquals(b.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId()); verifyNoInteractions(broker);
    }
    @ParameterizedTest @EnumSource(RoomType.class)
    void concurrentSendsUseServerIdOrder(RoomType type) throws Exception {
        tx.executeWithoutResult(s -> em.find(Room.class, room.getId()).setType(type));
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return send("a"); });
            var b = pool.submit(() -> { start.await(); return send("b"); }); start.countDown();
            long latest = Math.max(a.get(15, TimeUnit.SECONDS).getId(), b.get(15, TimeUnit.SECONDS).getId());
            assertEquals(latest, rooms.findById(room.getId()).orElseThrow().getLastMessageId());
            assertEquals(latest, history().getLast().getId());
            var page = service.getRoomMessages(alice.getUsername(), room.getId(), null, 1);
            assertEquals(latest, page.getFirst().getId());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentClearAndSendLeavePreviewConsistentWithVisibleHistory() throws Exception {
        send("before");
        CountDownLatch start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var clear = pool.submit(() -> { start.await(); service.clearRoomMessages(room.getId(), alice.getUsername()); return null; });
            var send = pool.submit(() -> { start.await(); return send("racing"); }); start.countDown();
            clear.get(15, TimeUnit.SECONDS); send.get(15, TimeUnit.SECONDS);
            var visible = history(); Room persisted = rooms.findById(room.getId()).orElseThrow();
            assertEquals(visible.isEmpty() ? null : visible.getLast().getId(), persisted.getLastMessageId());
            assertEquals(visible.isEmpty() ? null : visible.getLast().getContent(), persisted.getLastMessageContent());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentDeleteAndSendCannotRestoreDeletedPreview() throws Exception {
        var old = send("old"); CountDownLatch start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var delete = pool.submit(() -> { start.await(); service.deleteMessage(old.getId(), alice.getUsername()); return null; });
            var send = pool.submit(() -> { start.await(); return send("new"); }); start.countDown();
            delete.get(15, TimeUnit.SECONDS); var latest = send.get(15, TimeUnit.SECONDS);
            assertEquals(latest.getId(), rooms.findById(room.getId()).orElseThrow().getLastMessageId());
            assertEquals(List.of(latest.getId()), history().stream().map(ShortMessageWrapped::getId).toList());
        } finally { pool.shutdownNow(); }
    }
    @Test void longMessagePersistsFullHistoryWithBoundedPreview() {
        var message = send("x".repeat(1000));
        assertEquals(1000, history().getFirst().getContent().length());
        assertEquals(255, rooms.findById(room.getId()).orElseThrow().getLastMessageContent().length());
        service.editMessage(message.getId(), alice.getUsername(), "y".repeat(1100));
        assertEquals(1100, history().getFirst().getContent().length());
        assertEquals("y".repeat(255), rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        String emoji = new String(Character.toChars(0x1F600));
        service.editMessage(message.getId(), alice.getUsername(), emoji.repeat(300));
        assertEquals(emoji.repeat(300), history().getFirst().getContent());
        assertEquals(emoji.repeat(255), rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
    }
    @Test void membershipRevokedDuringUploadCompensatesAndNeverCreatesMessage() {
        doAnswer(i -> {
            uploaded.add(i.getArgument(0));
            tx.executeWithoutResult(s -> em.find(Room.class, room.getId()).getMembers().removeIf(u -> u.getId().equals(alice.getId())));
            return null;
        }).when(storage).uploadFile(anyString(), any(), anyLong(), anyString());
        assertThrows(com.zvonok.exception.UserNotMemberRoomException.class, () -> upload(1));
        assertEquals(uploaded, removed);
        assertEquals(0, messages.countByRoomIdAndDeletedAtIsNull(room.getId())); verifyNoInteractions(broker);
    }
    @Test void invalidSecondFileIsRejectedBeforeFirstS3Upload() {
        assertThrows(com.zvonok.exception.InvalidMessageAttachmentException.class,
                () -> service.sendMessageWithAttachments(alice.getUsername(), room.getId(), "",
                List.of(file("ok"), new MockMultipartFile("files", "bad", "text/html", new byte[]{1})),
                AttachmentType.AUDIO, 1000L, null, null));
        verifyNoInteractions(storage, broker); assertTrue(history().isEmpty());
    }
    @Test void successfulAttachmentsAreUniqueAndPersistOnce() {
        var sent = upload(2);
        assertEquals(2, sent.getAttachments().size()); assertEquals(2, uploaded.stream().distinct().count());
        assertTrue(removed.isEmpty());
        tx.executeWithoutResult(s -> assertEquals(2, messages.findById(sent.getId()).orElseThrow().getAttachments().size()));
        assertEquals("[voice]", rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
        send("newer"); service.deleteMessage(rooms.findById(room.getId()).orElseThrow().getLastMessageId(), alice.getUsername());
        assertEquals("[voice]", rooms.findById(room.getId()).orElseThrow().getLastMessageContent());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void uploadFailureCleansOnlyAttemptedNewKeys(int failureIndex) {
        AtomicInteger attempt = new AtomicInteger();
        doAnswer(i -> { uploaded.add(i.getArgument(0)); if (attempt.incrementAndGet() == failureIndex) throw new IllegalStateException("S3 unavailable"); return null; })
                .when(storage).uploadFile(anyString(), any(), anyLong(), anyString());
        assertThrows(IllegalStateException.class, () -> upload(2));
        assertEquals(uploaded, removed); assertTrue(history().isEmpty()); verifyNoInteractions(broker);
    }
    @Test void databaseFailureAtCommitAfterUploadRollsBackAndCompensates() {
        tx.executeWithoutResult(s -> em.createNativeQuery("ALTER TABLE room ADD CONSTRAINT stage6_reject_preview CHECK (id <> "
                + room.getId() + " OR last_message_content IS DISTINCT FROM '[voice]')").executeUpdate());
        try {
            assertThrows(RuntimeException.class, () -> upload(1));
            assertEquals(uploaded, removed); assertTrue(history().isEmpty()); verifyNoInteractions(broker);
            assertNull(rooms.findById(room.getId()).orElseThrow().getLastMessageId());
        } finally {
            tx.executeWithoutResult(s -> em.createNativeQuery("ALTER TABLE room DROP CONSTRAINT stage6_reject_preview").executeUpdate());
        }
    }
    @Test void brokerFailureAfterAttachmentCommitNeverDeletesUploadedObjects() {
        doThrow(new IllegalStateException("broker down")).when(broker).convertAndSendToUser(anyString(), anyString(), any(Object.class));
        var sent = assertDoesNotThrow(() -> upload(1));
        assertTrue(messages.existsById(sent.getId())); assertTrue(removed.isEmpty());
        assertEquals(sent.getId(), history().getFirst().getId());
    }
    @ParameterizedTest @EnumSource(AttachmentType.class)
    void eachSupportedAttachmentTypePersistsReferenceOnlyAfterUpload(AttachmentType type) {
        String contentType = switch (type) { case IMAGE -> "image/png"; case AUDIO -> "audio/webm"; default -> "video/webm"; };
        var file = new MockMultipartFile("files", "media", contentType, new byte[]{1,2,3});
        var sent = service.sendMessageWithAttachments(alice.getUsername(), room.getId(), "", List.of(file), type, 1000L, null, null);
        assertEquals(type, sent.getAttachments().getFirst().type());
        assertEquals(1, uploaded.size()); assertTrue(removed.isEmpty());
    }
    @Test void compensationFailureLeavesOldAttachmentUntouchedAndKeepsOriginalFailure() {
        var existing = upload(1); String oldKey = uploaded.getFirst(); uploaded.clear();
        doAnswer(i -> { uploaded.add(i.getArgument(0)); throw new IllegalStateException("upload failed"); })
                .when(storage).uploadFile(anyString(), any(), anyLong(), anyString());
        doAnswer(i -> { removed.add(i.getArgument(0)); throw new IllegalStateException("cleanup failed"); }).when(storage).deleteFile(anyString());
        assertEquals("upload failed", assertThrows(IllegalStateException.class, () -> upload(1)).getMessage());
        assertEquals(uploaded, removed); assertFalse(removed.contains(oldKey)); assertTrue(messages.existsById(existing.getId()));
    }
    ShortMessageWrapped uploadKey(String key, String content) {
        return service.sendMessageWithAttachments(alice.getUsername(), room.getId(), content,
                List.of(file("voice")), AttachmentType.AUDIO, 1000L, null, null, key);
    }
    @Test void repeatedUploadRequestReturnsSameMessageWithoutNewStorageOrEvents() {
        String key = UUID.randomUUID().toString();
        var first = uploadKey(key, "same"); clearInvocations(broker);
        var retry = uploadKey(key, "same");
        assertEquals(first.getId(), retry.getId()); assertEquals(1, uploaded.size()); assertEquals(1, history().size());
        assertEquals(first.getAttachments().getFirst().id(), retry.getAttachments().getFirst().id());
        assertTrue(removed.isEmpty()); verifyNoInteractions(broker);
        assertThrows(com.zvonok.exception.InvalidMessageAttachmentException.class, () -> uploadKey(key, "different"));
        assertEquals(1, uploaded.size());
    }
    @Test void deletedUploadRetryCannotRecreateMessageOrDeleteExistingObject() {
        String key = UUID.randomUUID().toString(); var sent = uploadKey(key, "same");
        service.deleteMessage(sent.getId(), alice.getUsername()); clearInvocations(broker);
        assertThrows(com.zvonok.exception.InvalidMessageAttachmentException.class, () -> uploadKey(key, "same"));
        assertEquals(1, uploaded.size()); assertTrue(removed.isEmpty()); assertTrue(history().isEmpty()); verifyNoInteractions(broker);
    }
    @Test void uploadIdempotencyIsScopedToSenderAndReauthorizesRetries() {
        String key = UUID.randomUUID().toString(); var first = uploadKey(key, "same");
        var other = service.sendMessageWithAttachments(bob.getUsername(), room.getId(), "same", List.of(file("voice")),
                AttachmentType.AUDIO, 1000L, null, null, key);
        assertNotEquals(first.getId(), other.getId()); assertEquals(2, uploaded.size());
        tx.executeWithoutResult(s -> em.find(Room.class, room.getId()).getMembers().removeIf(u -> u.getId().equals(alice.getId())));
        assertThrows(com.zvonok.exception.UserNotMemberRoomException.class, () -> uploadKey(key, "same"));
        assertEquals(2, uploaded.size()); assertTrue(removed.isEmpty());
    }
    @Test void concurrentUploadRetriesPersistOneMessageAndCleanUnusedStaging() throws Exception {
        String key = UUID.randomUUID().toString();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        List<String> keys = new java.util.concurrent.CopyOnWriteArrayList<>();
        doAnswer(i -> { keys.add(i.getArgument(0)); barrier.await(10, TimeUnit.SECONDS); return null; })
                .when(storage).uploadFile(anyString(), any(), anyLong(), anyString());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> uploadKey(key, "same")); var b = pool.submit(() -> uploadKey(key, "same"));
            var first = a.get(15, TimeUnit.SECONDS); var second = b.get(15, TimeUnit.SECONDS);
            assertEquals(first.getId(), second.getId()); assertEquals(1, history().size());
            assertEquals(2, keys.size()); assertEquals(1, removed.size());
            String persisted = tx.execute(status -> messages.findById(first.getId()).orElseThrow().getAttachments().getFirst().getStorageKey());
            assertFalse(removed.contains(persisted));
            verify(broker, times(2)).convertAndSendToUser(anyString(), eq("/queue/messages"), any(Object.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void migrationIsRepeatableOnEmptyExistingAndAlreadyUpdatedSchemas() throws Exception {
        String sql = Files.readString(Path.of("ops/sql/stage6-call-media-observations.sql"));
        try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var st = c.createStatement()) {
            st.execute("CREATE SCHEMA stage6_migration_test"); st.execute("SET search_path TO stage6_migration_test");
            st.execute(sql); st.execute(sql);
            String uploadSql = Files.readString(Path.of("ops/sql/stage6-message-upload-idempotency.sql"));
            st.execute(uploadSql); st.execute(uploadSql);
            st.execute("CREATE TABLE call_session (id bigint primary key, marker text)");
            st.execute("INSERT INTO call_session VALUES (1, 'keep')"); st.execute(sql); st.execute(sql);
            try (var rs = st.executeQuery("SELECT marker, media_empty_since, media_observed_at FROM call_session")) {
                assertTrue(rs.next()); assertEquals("keep", rs.getString(1)); assertNull(rs.getTimestamp(2)); assertNull(rs.getTimestamp(3));
            }
            st.execute("UPDATE call_session SET media_empty_since = timestamp '2026-10-18 00:00:00'"); st.execute(sql);
            try (var rs = st.executeQuery("SELECT media_empty_since FROM call_session")) { assertTrue(rs.next()); assertNotNull(rs.getTimestamp(1)); }
            st.execute("CREATE TABLE message (id bigint primary key, sender_id bigint, content text)");
            st.execute("INSERT INTO message VALUES (1, 10, 'keep')"); st.execute(uploadSql); st.execute(uploadSql);
            st.execute("UPDATE message SET upload_request_key = 'request', upload_request_hash = 'hash'"); st.execute(uploadSql);
            try (var rs = st.executeQuery("SELECT content, upload_request_key FROM message")) {
                assertTrue(rs.next()); assertEquals("keep", rs.getString(1)); assertEquals("request", rs.getString(2));
            }
            st.execute("INSERT INTO message(id, sender_id, content) VALUES (2, 10, 'also keep'), (3, 10, 'keep null keys')");
            assertThrows(java.sql.SQLException.class, () -> st.execute("UPDATE message SET upload_request_key = 'request' WHERE id = 2"));
            try (var rs = st.executeQuery("SELECT count(*) FROM message")) { assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); }
            st.execute("SET search_path TO public"); st.execute(sql); st.execute(uploadSql);
        }
    }
}
