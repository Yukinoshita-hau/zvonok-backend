package com.zvonok.service;

import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.zvonok.exception.FileStorageNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

	private final AmazonS3 s3Client;

	private final String bucketName = "zvonok-avatars";

	public void uploadFile(String key, InputStream inputStream, long contentLength,
			String contentType) {
		InputStream validated = StorageUploadValidation.validate(inputStream, contentLength, contentType);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentLength);
		metadata.setContentType(StorageUploadValidation.contentType(contentType));
		s3Client.putObject(new PutObjectRequest(bucketName, key, validated, metadata)
				.withCannedAcl(CannedAccessControlList.Private));
	}

	public S3Object downloadFile(String key) {
		try {
			return s3Client.getObject(bucketName, key);
		} catch (AmazonS3Exception e) {
			if ("NoSuchKey".equals(e.getErrorCode())) {
				throw new FileStorageNotFoundException(
						HttpResponseMessage.HTTP_FILE_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
			}
			throw e;
		}
	}

	public void deleteFile(String key) {
		s3Client.deleteObject(bucketName, key);
	}

	public List<S3ObjectSummary> listFiles() {
		return s3Client.listObjectsV2(bucketName).getObjectSummaries();
	}
}
