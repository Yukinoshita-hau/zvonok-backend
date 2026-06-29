package com.zvonok.controller;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.zvonok.model.MessageAttachment;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.MessageService;
import com.zvonok.service.S3Service;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message-attachments")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class MessageAttachmentController {

	private final MessageService messageService;
	private final S3Service s3Service;

	@GetMapping("/{attachmentId}/download")
	public ResponseEntity<Resource> download(@PathVariable Long attachmentId,
			@AuthenticationPrincipal UserPrincipal principal) {
		MessageAttachment attachment = messageService.getAttachmentForDownload(attachmentId,
				principal.getUsername());
		S3Object object = s3Service.downloadFile(attachment.getStorageKey());
		ObjectMetadata metadata = object.getObjectMetadata();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(resolveContentType(attachment, metadata));
		headers.setContentLength(attachment.getSizeBytes());
		headers.set(HttpHeaders.CONTENT_DISPOSITION,
				"inline; filename=\"" + attachment.getOriginalFileName() + "\"");

		return ResponseEntity.ok().headers(headers)
				.body(new InputStreamResource(object.getObjectContent()));
	}

	private MediaType resolveContentType(MessageAttachment attachment, ObjectMetadata metadata) {
		String contentType = attachment.getContentType();
		if (contentType == null || contentType.isBlank()) {
			contentType = metadata.getContentType();
		}
		if (contentType == null || contentType.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		return MediaType.parseMediaType(contentType);
	}
}
