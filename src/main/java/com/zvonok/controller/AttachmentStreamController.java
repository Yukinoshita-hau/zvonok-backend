package com.zvonok.controller;

import com.zvonok.model.MessageAttachment;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.AttachmentMediaAccess;
import com.zvonok.service.AttachmentRange;
import com.zvonok.service.S3Service;
import com.amazonaws.services.s3.model.S3Object;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/message-attachments")
@RequiredArgsConstructor
public class AttachmentStreamController {
    private final AttachmentMediaAccess access;
    private final S3Service storage;

    @PostMapping("/{id}/playback-access")
    public ResponseEntity<AttachmentMediaAccess.PlaybackAccess> playback(@PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(access.issuePlayback(id, principal.getUsername()));
    }

    @PostMapping("/{id}/access")
    public ResponseEntity<AttachmentMediaAccess.Access> issue(@PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(access.issue(id, principal.getUsername()));
    }

    @PostMapping("/{id}/download-access")
    public ResponseEntity<AttachmentMediaAccess.PlaybackAccess> download(@PathVariable long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(access.issueDownload(id, principal.getUsername()));
    }

    @RequestMapping(value = "/{id}/stream", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<InputStreamResource> stream(@PathVariable long id,
            @RequestParam String access, @RequestParam(defaultValue = "false") boolean download,
            @RequestHeader(value = "Range", required = false) String rangeHeader, HttpServletRequest request) {
        MessageAttachment attachment = this.access.authorize(id, access);
        long size = attachment.getSizeBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        String mime = attachment.getContentType();
        boolean inline = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "video/mp4", "video/webm",
                "video/quicktime", "audio/mpeg", "audio/ogg", "audio/webm", "audio/wav", "audio/mp4", "application/pdf",
                "text/plain", "text/markdown").contains(mime);
        headers.setContentType(inline ? MediaType.parseMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition((download || !inline ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(attachment.getOriginalFileName().replaceAll("[\\r\\n\\x00]", "_"), StandardCharsets.UTF_8).build());
        AttachmentRange range;
        try { range = AttachmentRange.parse(rangeHeader, size); }
        catch (IllegalArgumentException invalid) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
            return ResponseEntity.status(416).headers(headers).build();
        }
        headers.setContentLength(range.length());
        if (rangeHeader != null) headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + size);
        if (request.getMethod().equals("HEAD")) return ResponseEntity.status(rangeHeader == null ? 200 : 206).headers(headers).build();
        S3Object object = storage.downloadRange(attachment.getStorageKey(), rangeHeader == null ? null : range);
        return ResponseEntity.status(rangeHeader == null ? 200 : 206).headers(headers)
                .body(new InputStreamResource(object.getObjectContent()));
    }
}
