package com.zvonok.service;

import com.zvonok.exception.FileStorageNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.UserRepository;
import com.zvonok.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/** Resolve existing domain references before touching storage. Unknown keys fail closed. */
@Service
@RequiredArgsConstructor
public class S3ObjectAccessService {
    private final MessageAttachmentRepository attachments;
    private final MessageAccessService messages;
    private final UserRepository users;
    private final RoomRepository roomReferences;

    @Value("${s3.localEndpoint}")
    private String endpoint;

    @Transactional(readOnly = true)
    public void requireRead(String key, String username) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("\\")
                || key.startsWith("/") || key.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw unavailable();
        }
        var linkedAttachments = attachments.findAllByStorageKey(key);
        if (!linkedAttachments.isEmpty()) {
            // All references must authorize; a public profile cannot declassify a private object.
            for (var attachment : linkedAttachments) {
                messages.getReadableMessage(attachment.getMessage().getId(), username);
            }
            return;
        }
        String url = endpoint.replaceAll("/+$", "") + "/" + key;
        if (key.startsWith("message-attachments/")) { throw unavailable(); }
        if (!users.existsByAvatarUrl(url) && !roomReferences.existsByAvatarUrlAndIsActiveTrue(url)) { throw unavailable(); }
    }

    public void rejectUnscopedMutation() {
        throw new InsufficientPermissionsException("Use a domain upload endpoint; generic storage mutations are disabled");
    }

    private FileStorageNotFoundException unavailable() {
        return new FileStorageNotFoundException("File not found");
    }
}
