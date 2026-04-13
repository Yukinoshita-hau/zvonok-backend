package com.zvonok.service;

import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

	private final AmazonS3 s3Client;

	private final String bucketName = "zvonok-avatars";

	public void uploadFile(String key, InputStream inputStream, long contentLength,
			String contentType) {
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentLength);
		metadata.setContentType(contentType);
		s3Client.putObject(bucketName, key, inputStream, metadata);
	}

	public S3Object downloadFile(String key) {
		System.out.println(key);
		return s3Client.getObject(bucketName, key);
	}

	public void deleteFile(String key) {
		s3Client.deleteObject(bucketName, key);
	}

	public List<S3ObjectSummary> listFiles() {
		return s3Client.listObjectsV2(bucketName).getObjectSummaries();
	}
}
