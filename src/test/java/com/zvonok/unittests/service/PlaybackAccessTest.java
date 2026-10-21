package com.zvonok.unittests.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.zvonok.model.MessageAttachment;
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.service.AttachmentMediaAccess;
import com.zvonok.service.MessageService;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaybackAccessTest {
    @Test void downloadSignatureIsPrivateGetWithSafeAttachmentDisposition() throws Exception {
        AmazonS3 client = mock(AmazonS3.class);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(URI.create("https://storage.invalid/download").toURL());
        new S3Service(client).downloadUrl("private/7", "name\r\n.zip", Instant.now().plusSeconds(900));
        var captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class); verify(client).generatePresignedUrl(captor.capture());
        var request = captor.getValue(); assertEquals(HttpMethod.GET, request.getMethod()); assertEquals("private/7", request.getKey());
        assertEquals("zvonok-data", request.getBucketName());
        assertEquals("application/octet-stream", request.getResponseHeaders().getContentType());
        assertTrue(request.getResponseHeaders().getContentDisposition().startsWith("attachment;"));
        assertFalse(request.getResponseHeaders().getContentDisposition().contains("\r"));
        assertFalse(request.getResponseHeaders().getContentDisposition().contains("\n"));
    }
    MessageService messages = mock(MessageService.class);
    S3Service storage = mock(S3Service.class);
    AttachmentMediaAccess access = new AttachmentMediaAccess(messages, storage);

    @Test void scopedOriginalWithPracticalExpiry() {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setType(AttachmentType.VIDEO); attachment.setStorageKey("private/object");
        attachment.setContentType("video/mp4"); attachment.setSizeBytes(42L);
        when(messages.getAttachmentForDownload(7L, "member")).thenReturn(attachment);
        when(storage.playbackUrl(eq("private/object"), eq("video/mp4"), any())).thenReturn("https://storage.invalid/signed");
        var grant = access.issuePlayback(7, "member");
        assertEquals(7, grant.attachmentId()); assertEquals(42, grant.byteSize());
        assertEquals("original", grant.variant());
        assertTrue(grant.expiresAt().isAfter(Instant.now().plusSeconds(890)));
        assertTrue(grant.expiresAt().isBefore(Instant.now().plusSeconds(901)));
    }
    @Test void deniedMembershipDoesNotSign() {
        when(messages.getAttachmentForDownload(7L, "outsider")).thenThrow(new IllegalStateException("denied"));
        assertThrows(IllegalStateException.class, () -> access.issuePlayback(7, "outsider"));
        verifyNoInteractions(storage);
    }
    @Test void nonMediaOriginalCannotGetPlaybackGrant() {
        var attachment = new MessageAttachment(); attachment.setType(AttachmentType.FILE);
        when(messages.getAttachmentForDownload(7L, "member")).thenReturn(attachment);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> access.issuePlayback(7, "member"));
        verifyNoInteractions(storage);
    }
    @Test void internalOrHttpStorageAddressCannotReachTheBrowser() {
        var attachment = new MessageAttachment(); attachment.setType(AttachmentType.AUDIO);
        attachment.setStorageKey("private/7"); attachment.setContentType("audio/webm"); attachment.setSizeBytes(10L);
        when(messages.getAttachmentForDownload(7L, "member")).thenReturn(attachment);
        when(storage.playbackUrl(anyString(), anyString(), any())).thenReturn("http://internal/object");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> access.issuePlayback(7, "member"));
    }
    @Test void signatureIsGetOnOnePrivateObject() throws Exception {
        AmazonS3 client = mock(AmazonS3.class);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(URI.create("https://storage.invalid/signed").toURL());
        Instant expiry = Instant.now().plusSeconds(900);
        new S3Service(client).playbackUrl("private/7", "video/webm", expiry);
        var captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(captor.capture());
        var request = captor.getValue();
        assertEquals("zvonok-data", request.getBucketName()); assertEquals("private/7", request.getKey());
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals(expiry.toEpochMilli(), request.getExpiration().getTime());
        assertEquals("inline", request.getResponseHeaders().getContentDisposition());
    }
}
