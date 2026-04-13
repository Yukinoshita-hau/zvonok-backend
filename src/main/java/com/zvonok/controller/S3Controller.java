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
import lombok.RequiredArgsConstructor;

@RestController()
@RequestMapping("/s3")
@RequiredArgsConstructor
public class S3Controller {

	private final S3Service s3Service;

	@PostMapping("/upload")
	public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
		try {
			s3Service.uploadFile(file.getOriginalFilename(), file.getInputStream(), file.getSize(),
					file.getContentType());
			return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/download/{key}")
	public ResponseEntity<Resource> download(@PathVariable String key) {
		S3Object obj = s3Service.downloadFile(key);
		ObjectMetadata meta = obj.getObjectMetadata();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(meta.getContentType()));
		headers.setContentLength(meta.getContentLength());
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"");

		Resource resource = new InputStreamResource(obj.getObjectContent());
		return ResponseEntity.ok().headers(headers).body(resource);
	}

	@DeleteMapping("/delete/{key}")
	public ResponseEntity<Void> deleteFile(@PathVariable String key) {
		s3Service.deleteFile(key);
		return ResponseEntity.ok().build();
	}
}
