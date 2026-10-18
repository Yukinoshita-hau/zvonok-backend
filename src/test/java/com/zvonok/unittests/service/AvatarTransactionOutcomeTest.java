package com.zvonok.unittests.service;

import com.zvonok.model.User;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarTransactionOutcomeTest {
    UserRepository repository = mock(UserRepository.class);
    UserEventPublisherService publisher = mock(UserEventPublisherService.class);
    S3Service storage = mock(S3Service.class);
    UserService service = new UserService(repository, publisher, storage,
            mock(FriendshipRepository.class), mock(FriendRequestRepository.class), mock(RoomRepository.class));
    User user = new User();
    @BeforeEach void setup() {
        user.setUsername("avatar-test"); user.setAvatarUrl("http://test/retained-old-avatar");
        when(repository.findByUsername("avatar-test")).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(service, "endpoint", "http://test");
    }
    @AfterEach void clean() { TransactionSynchronizationManager.clear(); }
    void transaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    void upload() { service.uploadAvatar("avatar-test", new ByteArrayInputStream(new byte[]{1}), 1, "image/png", ".png"); }
    String stagedKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).uploadFile(key.capture(), any(), eq(1L), eq("image/png"));
        return key.getValue();
    }
    @Test void rolledBackAvatarDeletesOnlyNewKeyAndDoesNotPublishPhantomProfile() {
        transaction(); upload();
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        String key = stagedKey(); verify(storage).deleteFile(key);
        verify(storage, never()).deleteFile("retained-old-avatar");
        verifyNoInteractions(publisher);
    }
    @Test void committedAvatarPublishesAfterCommitAndRetainsOldObjectPolicy() {
        transaction(); upload(); verifyNoInteractions(publisher);
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(publisher).publishUserProfileUpdated(user); verify(storage, never()).deleteFile(anyString());
    }
    @Test void unknownTransactionOutcomeDoesNotDeletePossiblyCommittedObject() {
        transaction(); upload();
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        verify(storage, never()).deleteFile(anyString());
    }
    @Test void repositoryFailureWithoutTransactionCleansNewKeyAndPreservesOriginalFailure() {
        var failure = new IllegalStateException("persistence failed");
        when(repository.save(user)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, this::upload));
        String key = stagedKey(); verify(storage).deleteFile(key); verifyNoInteractions(publisher);
    }
}

