package com.zvonok.unittests.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalStorageConfigurationTest {
    @Test void diagnosticHttpRequiresExplicitOptInAndExactLoopback() {
        S3Service service = new S3Service(mock(AmazonS3.class));
        assertFalse(service.allowsDiagnosticUrl("http://localhost:19000/b/key"));
        ReflectionTestUtils.setField(service, "allowLoopbackHttp", true);
        assertFalse(service.allowsDiagnosticUrl("http://localhost:19000/b/key"));
        ReflectionTestUtils.setField(service, "provider", "rustfs");
        assertTrue(service.allowsDiagnosticUrl("http://127.0.0.1:19000/b/key"));
        assertFalse(service.allowsDiagnosticUrl("http://rustfs:9000/b/key"));
        assertFalse(service.allowsDiagnosticUrl("http://localhost.evil/b/key"));
        assertFalse(service.allowsDiagnosticUrl("http://user@localhost/b/key"));
    }
    @Test void rustfsKeepsPrivateAclAndUsesSeparateSignerWithoutChangingKey() throws Exception {
        AmazonS3 internal = mock(AmazonS3.class), external = mock(AmazonS3.class);
        S3Service service = new S3Service(internal);
        ReflectionTestUtils.setField(service, "provider", "rustfs");
        ReflectionTestUtils.setField(service, "bucketName", "diagnostic");
        service.setSigningClient(external);
        when(internal.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            assertEquals(CannedAccessControlList.Private, request.getCannedAcl());
            assertEquals("diagnostic", request.getBucketName());
            assertEquals("same-key", request.getKey());
            return null;
        });
        byte[] text = "diagnostic".getBytes();
        service.uploadFile("same-key", new ByteArrayInputStream(text), text.length, "text/plain");
        when(external.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(URI.create("https://storage.invalid/diagnostic/same-key").toURL());
        assertTrue(service.playbackUrl("same-key", "text/plain", Instant.now().plusSeconds(60)).startsWith("https://storage.invalid/"));
        verify(internal, never()).generatePresignedUrl(any(GeneratePresignedUrlRequest.class));
    }
}
