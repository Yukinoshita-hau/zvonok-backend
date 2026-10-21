package com.zvonok.service;

import com.zvonok.model.MessageAttachment;
import com.zvonok.model.enumeration.AttachmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AttachmentMediaAccess {
    private final MessageService messages;
    private final S3Service storage;
    @Value("${s3.access-ttl-seconds:900}")
    private long accessTtlSeconds = 900;
    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private record Grant(long attachmentId, String username, Instant expiresAt) { }
    public record Access(String path, Instant expiresAt) { }
    public record PlaybackAccess(long attachmentId, String url, Instant expiresAt, String mimeType,
            long byteSize, String variant, boolean rangeCapable) { }

    public PlaybackAccess issuePlayback(long attachmentId, String username) {
        MessageAttachment attachment = messages.getAttachmentForDownload(attachmentId, username);
        String mime = attachment.getContentType() == null ? "" : attachment.getContentType();
        if (attachment.getType() != AttachmentType.VIDEO
                && attachment.getType() != AttachmentType.VIDEO_NOTE
                && attachment.getType() != AttachmentType.AUDIO
                && !(attachment.getType() == AttachmentType.IMAGE
                    && Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(mime))
                && !(attachment.getType() == AttachmentType.FILE
                    && Set.of("application/pdf", "text/plain", "text/markdown", "application/json", "text/csv").contains(mime))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        Instant expiry = Instant.now().plusSeconds(accessTtlSeconds);
        String url = storage.playbackUrl(attachment.getStorageKey(), attachment.getContentType(), expiry);
        if (!url.startsWith("https://") && !storage.allowsDiagnosticUrl(url)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Playback storage requires HTTPS");
        return new PlaybackAccess(attachmentId, url, expiry, attachment.getContentType(),
                attachment.getSizeBytes(), "original", true);
    }

    public PlaybackAccess issueDownload(long attachmentId, String username) {
        MessageAttachment attachment = messages.getAttachmentForDownload(attachmentId, username);
        Instant expiry = Instant.now().plusSeconds(accessTtlSeconds);
        String url = storage.downloadUrl(attachment.getStorageKey(), attachment.getOriginalFileName(), expiry);
        if (!url.startsWith("https://") && !storage.allowsDiagnosticUrl(url)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        return new PlaybackAccess(attachmentId, url, expiry, "application/octet-stream",
                attachment.getSizeBytes(), "original", true);
    }

    public synchronized Access issue(long attachmentId, String username) {
        messages.getAttachmentForDownload(attachmentId, username);
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (grants.size() >= 4096) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiry = now.plusSeconds(300);
        grants.put(token, new Grant(attachmentId, username, expiry));
        return new Access("/message-attachments/" + attachmentId + "/stream?access=" + token, expiry);
    }

    public MessageAttachment authorize(long attachmentId, String token) {
        Grant grant = token == null ? null : grants.get(token);
        if (grant == null || grant.attachmentId() != attachmentId || !grant.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Membership and deletion are rechecked for every request, including Range/HEAD.
        return messages.getAttachmentForDownload(attachmentId, grant.username());
    }
}
