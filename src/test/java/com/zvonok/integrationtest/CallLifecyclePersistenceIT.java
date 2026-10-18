package com.zvonok.integrationtest;

import com.zvonok.config.CallLifecycleProperties;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.controller.dto.LeaveCallDto;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.*;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true", "spring.jpa.show-sql=false"})
@ContextConfiguration(classes = CallLifecyclePersistenceIT.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CallLifecyclePersistenceIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Configuration
    @EntityScan(basePackageClasses = CallSession.class)
    @EnableJpaRepositories(basePackageClasses = CallSessionRepository.class)
    @Import({CallSessionService.class, CallSessionCleanupService.class, CallLifecycleProperties.class, CallRestoreStateService.class})
    static class Config {}

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired CallSessionRepository calls;
    @Autowired CallParticipantRepository participants;
    @Autowired CallSessionService sessions;
    @Autowired CallSessionCleanupService cleanup;
    @Autowired CallRestoreStateService restore;
    @MockitoBean RoomService rooms;
    @MockitoBean UserService users;
    @MockitoBean CallEventPublisher events;
    @MockitoBean LiveKitRoomAdminService media;
    TransactionTemplate tx;
    Long callId;
    User user;
    Room room;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            user = new User(); user.setUsername("u" + UUID.randomUUID().toString().substring(0, 8));
            user.setDisplayName("Caller"); user.setEmail(user.getUsername() + "@test.invalid"); user.setPassword("test-only"); em.persist(user);
            room = new Room(); room.setType(RoomType.GROUP); room.setMembers(new ArrayList<>(List.of(user))); em.persist(room);
            CallSession call = new CallSession(); call.setRoom(room); call.setRoomType(RoomType.GROUP);
            call.setCreatedBy(user); call.setHostUser(user); call.setLivekitRoomName("it-" + UUID.randomUUID());
            call.setStatus(CallSessionStatus.ACTIVE); call.setStartedAt(LocalDateTime.now().minusMinutes(5)); em.persist(call);
            CallParticipant p = new CallParticipant(); p.setCallSession(call); p.setUser(user); p.setIdentity(user.getUsername());
            p.setStatus(CallParticipantStatus.ACCEPTED); p.setRole(CallParticipantRole.HOST); em.persist(p);
            em.flush(); callId = call.getId();
        });
        when(users.getUser(user.getUsername())).thenReturn(user);
        when(rooms.getRoom(room.getId(), user.getUsername())).thenReturn(room);
    }

    @Test void concurrentLeaveEndAndTokenRestoreSerializeAndPublishOnce() throws Exception {
        EndCallDto end = new EndCallDto(); end.setCallId(callId);
        LeaveCallDto leave = new LeaveCallDto(); leave.setCallId(callId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> jobs = List.of(
                pool.submit(() -> { await(start); sessions.end(user.getUsername(), end); }),
                pool.submit(() -> { await(start); sessions.leave(user.getUsername(), leave); }),
                pool.submit(() -> { await(start); try {
                    sessions.assertTokenStillAllowedAndTouch(callId, user.getUsername(), Set.of(CallSessionStatus.ACTIVE),
                            Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED));
                } catch (com.zvonok.exception.CallStateConflictException expected) { } }));
            start.countDown();
            for (Future<?> job : jobs) job.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(CallSessionStatus.ENDED, calls.findById(callId).orElseThrow().getStatus());
        CallParticipant p = participants.findByCallSessionIdAndUserId(callId, user.getId()).orElseThrow();
        assertEquals(CallParticipantStatus.LEFT, p.getStatus()); assertNotNull(p.getLeftAt());
        verify(events, times(1)).sendToUser(eq(user.getUsername()), argThat(e -> e.getType() == com.zvonok.service.dto.CallType.CALL_ENDED));
    }

    @Test void committedRecoveryInvalidatesEmptySnapshotAcrossTransactions() {
        var snapshot = cleanup.snapshots(0).stream().filter(s -> s.id().equals(callId)).findFirst().orElseThrow();
        sessions.assertTokenStillAllowedAndTouch(callId, user.getUsername(), Set.of(CallSessionStatus.ACTIVE), Set.of(CallParticipantStatus.ACCEPTED));
        cleanup.observeMedia(snapshot, true, true, LocalDateTime.now().plusMinutes(2));
        assertNull(calls.findById(callId).orElseThrow().getMediaEmptySince());
        assertEquals(CallSessionStatus.ACTIVE, calls.findById(callId).orElseThrow().getStatus());
        verifyNoInteractions(events);
    }

    @Test void rollbackDoesNotPublishOrDeleteMediaRoom() {
        tx.executeWithoutResult(status -> {
            EndCallDto end = new EndCallDto(); end.setCallId(callId);
            sessions.end(user.getUsername(), end);
            verifyNoInteractions(events, media);
            status.setRollbackOnly();
        });
        assertEquals(CallSessionStatus.ACTIVE, calls.findById(callId).orElseThrow().getStatus());
        assertEquals(CallParticipantStatus.ACCEPTED, participants.findByCallSessionIdAndUserId(callId, user.getId()).orElseThrow().getStatus());
        verifyNoInteractions(events, media);
    }
    @Test void restoreQueryRejectsRevokedMembership() {
        assertTrue(restore.findCandidate(user.getUsername()).isPresent());
        tx.executeWithoutResult(s -> em.find(Room.class, room.getId()).getMembers().clear());
        assertTrue(restore.findCandidate(user.getUsername()).isEmpty());
    }

    @Test void persistedGraceExpiresOnceAndEndedCallCannotRestore() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        cleanup.observeMedia(currentSnapshot(), true, false, now);
        assertTrue(restore.findCandidate(user.getUsername()).isPresent());
        cleanup.observeMedia(currentSnapshot(), true, false, now.plusSeconds(89));
        assertEquals(CallSessionStatus.ACTIVE, calls.findById(callId).orElseThrow().getStatus());
        var finalSnapshot = currentSnapshot();
        cleanup.observeMedia(finalSnapshot, true, true, now.plusSeconds(90));
        cleanup.observeMedia(finalSnapshot, true, true, now.plusSeconds(91));
        assertEquals(CallSessionStatus.ENDED, calls.findById(callId).orElseThrow().getStatus());
        assertTrue(restore.findCandidate(user.getUsername()).isEmpty());
        verify(events, times(1)).sendToUser(eq(user.getUsername()), any());
    }
    CallSessionCleanupService.Snapshot currentSnapshot() {
        return cleanup.snapshots(0).stream().filter(s -> s.id().equals(callId)).findFirst().orElseThrow();
    }
    static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
}
