package com.zvonok.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.zvonok.service.S3Service;
import com.zvonok.service.S3ObjectAccessService;
import java.security.Principal;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import lombok.RequiredArgsConstructor;

@RestController()
@RequestMapping("/s3")
@RequiredArgsConstructor
public class S3Controller {

	private final S3Service s3Service;
	private final S3ObjectAccessService access;

	@PostMapping("/upload")
	public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
		access.rejectUnscopedMutation();
		return ResponseEntity.status(403).build();
	}

	@GetMapping("/download/{*key}")
	public ResponseEntity<Resource> download(@PathVariable String key, Principal principal) {
		if (key.startsWith("/")) { key = key.substring(1); }
		access.requireRead(key, principal == null ? null : principal.getName());
		S3Object obj = s3Service.downloadFile(key);
		ObjectMetadata meta = obj.getObjectMetadata();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentLength(meta.getContentLength());
		headers.setContentDisposition(ContentDisposition.attachment().filename("download").build());
		headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
		headers.set("X-Content-Type-Options", "nosniff");
		if (meta.getContentType() != null
				&& Set.of("image/png", "image/jpeg", "image/gif", "image/webp").contains(meta.getContentType())) {
			headers.setContentType(MediaType.parseMediaType(meta.getContentType()));
		}

		Resource resource = new InputStreamResource(obj.getObjectContent());
		return ResponseEntity.ok().headers(headers).body(resource);
	}

	@DeleteMapping("/delete/{key}")
	public ResponseEntity<Void> deleteFile(@PathVariable String key) {
		access.rejectUnscopedMutation();
		return ResponseEntity.ok().build();
	}
}
