package com.zvonok.service;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import java.util.List;
import java.time.Instant;
import java.net.URI;
import java.util.Date;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

	private final AmazonS3 s3Client;

	@Value("${s3.bucket:zvonok-data}")
	private String bucketName = "zvonok-data";
	@Value("${s3.provider:external}")
	private String provider = "external";
	@Value("${s3.upload-execution-timeout-ms:300000}")
	private int uploadExecutionTimeout = 300_000;
	@Value("${s3.allow-loopback-http:false}")
	private boolean allowLoopbackHttp;
	private AmazonS3 signingClient;

	@Autowired(required = false)
	public void setSigningClient(@Qualifier("s3SigningClient") AmazonS3 client) {
		signingClient = client;
	}

	private AmazonS3 signer() { return signingClient == null ? s3Client : signingClient; }

	public boolean allowsDiagnosticUrl(String url) {
		URI uri = URI.create(url);
		return allowLoopbackHttp && ("rustfs".equals(provider) || "minio".equals(provider)) && "http".equals(uri.getScheme())
				&& uri.getUserInfo() == null && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
	}

	public String playbackUrl(String key, String mimeType, Instant expiresAt) {
		return signer().generatePresignedUrl(new GeneratePresignedUrlRequest(bucketName, key)
				.withMethod(HttpMethod.GET).withExpiration(Date.from(expiresAt))
				.withResponseHeaders(new ResponseHeaderOverrides().withContentType(mimeType)
						.withContentDisposition("inline").withCacheControl("private, no-store"))).toExternalForm();
	}

	public String downloadUrl(String key, String filename, Instant expiresAt) {
		String safeName = filename == null ? "file" : filename.replaceAll("[\\r\\n\\x00]", "_");
		return signer().generatePresignedUrl(new GeneratePresignedUrlRequest(bucketName, key)
				.withMethod(HttpMethod.GET).withExpiration(Date.from(expiresAt))
				.withResponseHeaders(new ResponseHeaderOverrides().withContentType("application/octet-stream")
						.withContentDisposition(ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8).build().toString())
						.withCacheControl("private, no-store"))).toExternalForm();
	}

	public void uploadFile(String key, InputStream inputStream, long contentLength,
			String contentType) {
		long started = System.nanoTime();
		S3UploadMetrics metrics = new S3UploadMetrics();
		InputStream validated = StorageUploadValidation.validate(inputStream, contentLength, contentType);
		long validatedAt = System.nanoTime(), stagedAt = 0, putAt = 0;
		Throwable failure = null;
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentLength);
		metadata.setContentType(StorageUploadValidation.contentType(contentType));
		Path staged = null;
		try {
			// A file lets the SDK reopen a failed PUT without buffering the whole upload in heap.
			staged = Files.createTempFile("zvonok-upload-", ".tmp");
			Files.copy(validated, staged, StandardCopyOption.REPLACE_EXISTING);
			if (Files.size(staged) != contentLength) throw new IOException("Upload length mismatch");
			stagedAt = System.nanoTime();
			PutObjectRequest request = new PutObjectRequest(bucketName, key, staged.toFile()).withMetadata(metadata)
					.withCannedAcl(CannedAccessControlList.Private);
			// The measured container-to-S3 path needs ~260 seconds for the 50 MiB policy cap.
			request.setSdkClientExecutionTimeout(uploadExecutionTimeout);
			request.setRequestMetricCollector(metrics);
			request.setGeneralProgressListener(metrics.progress);
			putAt = System.nanoTime();
			s3Client.putObject(request);
		} catch (IOException error) {
			failure = error;
			throw new UncheckedIOException("Unable to stage attachment upload", error);
		} catch (RuntimeException error) {
			failure = error;
			throw error;
		} finally {
			if (staged != null) {
				try { Files.deleteIfExists(staged); }
				catch (IOException cleanupError) { log.warn("Unable to remove staged upload {}", staged, cleanupError); }
			}
			metrics.finish(contentLength, (validatedAt - started) / 1_000_000,
					stagedAt == 0 ? -1 : (stagedAt - validatedAt) / 1_000_000,
					putAt == 0 ? -1 : (System.nanoTime() - putAt) / 1_000_000, staged == null || !Files.exists(staged), failure);
		}
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

	public S3Object downloadRange(String key, AttachmentRange range) {
		if (range == null) return downloadFile(key);
		try {
			return s3Client.getObject(new com.amazonaws.services.s3.model.GetObjectRequest(bucketName, key)
					.withRange(range.start(), range.end()));
		} catch (AmazonS3Exception failure) {
			if (failure.getStatusCode() == 404) throw new FileStorageNotFoundException("File unavailable");
			throw failure;
		}
	}

	public List<S3ObjectSummary> listFiles() {
		return s3Client.listObjectsV2(bucketName).getObjectSummaries();
	}
}
