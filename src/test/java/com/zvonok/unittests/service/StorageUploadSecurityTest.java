package com.zvonok.unittests.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.zvonok.exception.InvalidMessageAttachmentException;
import com.zvonok.model.User;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import com.zvonok.service.dto.UpdateUserDto;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageUploadSecurityTest {
    AmazonS3 client = mock(AmazonS3.class);
    S3Service storage = new S3Service(client);
    UserRepository repository = mock(UserRepository.class);
    UserService users = new UserService(repository, mock(UserEventPublisherService.class), storage,
            mock(FriendshipRepository.class), mock(FriendRequestRepository.class), mock(RoomRepository.class));
    User user = new User();
    byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ1cAAAAASUVORK5CYII=");

    @BeforeEach void setup() {
        user.setId(1L); user.setUsername("alice"); user.setAvatarUrl("http://localhost/old.png");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(users, "endpoint", "http://localhost");
    }
    void upload(String extension) { users.uploadAvatar("alice", new ByteArrayInputStream(png), png.length, "image/png", extension); }

    @Test void ownerUploadsAndReplacesAvatarWithoutDeletingOldObject() throws IOException {
        java.util.List<java.nio.file.Path> staged = new java.util.ArrayList<>();
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            var path = request.getFile().toPath(); staged.add(path);
            assertArrayEquals(png, java.nio.file.Files.readAllBytes(path));
            assertArrayEquals(png, java.nio.file.Files.readAllBytes(path));
            return null;
        });
        upload("../../bob.png"); String first = user.getAvatarUrl();
        upload("../../bob.png"); String second = user.getAvatarUrl();
        assertNotEquals(first, second); assertTrue(first.matches("http://localhost/avatar_[0-9a-f-]{36}"));
        ArgumentCaptor<PutObjectRequest> capture = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(capture.capture());
        for (var request : capture.getAllValues()) {
            assertEquals(CannedAccessControlList.Private, request.getCannedAcl());
            assertEquals(png.length, request.getMetadata().getContentLength());
            assertEquals(300_000, request.getSdkClientExecutionTimeout());
            assertNotNull(request.getRequestMetricCollector());
        }
        staged.forEach(path -> assertFalse(java.nio.file.Files.exists(path)));
        verify(client, never()).deleteObject(anyString(), anyString()); verify(repository, times(2)).save(user);
    }
    @Test void storageFailureKeepsPreviousAvatar() {
        java.util.List<java.nio.file.Path> staged = new java.util.ArrayList<>();
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            staged.add(((PutObjectRequest) invocation.getArgument(0)).getFile().toPath());
            throw new IllegalStateException("storage unavailable");
        });
        assertThrows(IllegalStateException.class, () -> upload(".png")); assertEquals("http://localhost/old.png", user.getAvatarUrl());
        verify(repository, never()).save(any()); verify(client, never()).deleteObject(anyString(), anyString());
        staged.forEach(path -> assertFalse(java.nio.file.Files.exists(path)));
    }
    @Test void spoofedContentRejectedBeforeWrite() {
        assertThrows(InvalidMessageAttachmentException.class, () -> users.uploadAvatar("alice",
                new ByteArrayInputStream("<svg onload='evil()'>".getBytes()), 21, "image/png", ".png"));
        verifyNoInteractions(client); assertEquals("http://localhost/old.png", user.getAvatarUrl());
    }
    @ParameterizedTest @ValueSource(longs={0, -1, 10485761})
    void invalidAvatarSizeRejected(long size) {
        assertThrows(InvalidMessageAttachmentException.class, () -> users.uploadAvatar("alice", new ByteArrayInputStream(png), size, "image/png", ".png"));
        verifyNoInteractions(client);
    }
    @ParameterizedTest @ValueSource(strings={"image/svg+xml", "text/html", "application/javascript", "video/mp4"})
    void activeOrNonImageAvatarTypeRejected(String type) {
        assertThrows(InvalidMessageAttachmentException.class, () -> users.uploadAvatar("alice", new ByteArrayInputStream(png), png.length, type, ".png"));
        verifyNoInteractions(client);
    }
    @Test void profileUpdateCannotPublishArbitraryStorageObject() {
        UpdateUserDto dto = new UpdateUserDto(); dto.setAvatarUrl("http://localhost/private.png");
        assertThrows(InvalidMessageAttachmentException.class, () -> users.updateMyUser("alice", dto)); verifyNoInteractions(client);
    }
}
