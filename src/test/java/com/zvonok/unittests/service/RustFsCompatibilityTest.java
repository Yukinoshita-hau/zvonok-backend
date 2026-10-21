package com.zvonok.unittests.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUSTFS_ACCEPTANCE", matches = "true")
class RustFsCompatibilityTest {
    @Test
    @EnabledIfEnvironmentVariable(named="RUSTFS_RESTORE_DIRECTORY",matches=".+")
    void restoreArchivedDiagnosticObjects() throws Exception {
        Path directory=Path.of(System.getenv("RUSTFS_RESTORE_DIRECTORY")).toRealPath();
        var mapper=new ObjectMapper();
        var archive=mapper.readTree(directory.resolve("objects-manifest.json").toFile());
        var objects=archive.get("objects");assertTrue(objects.isArray());assertTrue(objects.size()>0);
        for(var object:objects){
            String filename=object.get("file").asText();assertTrue(filename.matches("object-[0-9]+\\.bin"));
            Path file=directory.resolve(filename).toRealPath();assertTrue(file.startsWith(directory));
            assertEquals(object.get("bytes").asLong(),Files.size(file));
            assertEquals(object.get("sha256").asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        var client=AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://127.0.0.1:19000","us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(System.getenv("RUSTFS_ACCESS_KEY"),System.getenv("RUSTFS_SECRET_KEY"))))
                .withPathStyleAccessEnabled(true).build();
        String bucket="stage5d-archive-"+UUID.randomUUID();
        try{
            client.createBucket(bucket);
            for(var object:objects){
                var metadata=new ObjectMetadata();metadata.setContentType(object.get("contentType").asText());
                if(object.hasNonNull("contentDisposition"))metadata.setContentDisposition(object.get("contentDisposition").asText());
                var userMetadata=new LinkedHashMap<String,String>();
                if(object.hasNonNull("userMetadata"))object.get("userMetadata").fields().forEachRemaining(entry->userMetadata.put(entry.getKey(),entry.getValue().asText()));
                metadata.setUserMetadata(userMetadata);
                String key=object.get("key").asText();
                client.putObject(new PutObjectRequest(bucket,key,directory.resolve(object.get("file").asText()).toFile()).withMetadata(metadata).withCannedAcl(CannedAccessControlList.Private));
                try(var restored=client.getObject(bucket,key)){
                    assertEquals(object.get("sha256").asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(restored.getObjectContent().readAllBytes())));
                    assertEquals(metadata.getContentType(),restored.getObjectMetadata().getContentType());
                    assertEquals(metadata.getContentDisposition(),restored.getObjectMetadata().getContentDisposition());
                    assertEquals(userMetadata,restored.getObjectMetadata().getUserMetadata());
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("storage/evidence/archive-object-restore.json").toFile(),Map.of("status","PASS","objects",objects.size(),"restoreBucket",bucket,"sourceObjectReads",0));
        }finally{client.shutdown();}
    }
    @Test
    @EnabledIfEnvironmentVariable(named="RUSTFS_BACKUP_DIRECTORY", matches=".+")
    void coordinatedDiagnosticObjectRestore() throws Exception {
        Path directory=Path.of(System.getenv("RUSTFS_BACKUP_DIRECTORY")).toAbsolutePath().normalize();
        Path root=Path.of(System.getenv().getOrDefault("RUSTFS_BACKUP_ROOT",Path.of(System.getenv("LOCALAPPDATA"),"Zvonok","backups").toString())).toRealPath();
        assertTrue(directory.toRealPath().startsWith(root));
        assertNotEquals(root,directory.toRealPath());
        var mapper=new ObjectMapper();
        var references=mapper.readTree(directory.resolve("references.json").toFile());
        var client=AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://127.0.0.1:19000","us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(System.getenv("RUSTFS_ACCESS_KEY"),System.getenv("RUSTFS_SECRET_KEY"))))
                .withPathStyleAccessEnabled(true).build();
        String restoredBucket="stage5d-restore-"+UUID.randomUUID();
        var objects=new ArrayList<Map<String,Object>>();
        try {
            client.createBucket(restoredBucket);
            int index=0;
            for(var reference:references){
                String key=reference.get("key").asText();
                var metadata=client.getObjectMetadata("zvonok-stage5d-data",key);
                Path file=directory.resolve("object-"+(index++)+".bin");
                try(var source=client.getObject("zvonok-stage5d-data",key)){Files.copy(source.getObjectContent(),file);}
                assertEquals(reference.get("bytes").asLong(),Files.size(file));
                byte[] bytes=Files.readAllBytes(file);String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                var restoredMetadata=new ObjectMetadata();restoredMetadata.setContentType(metadata.getContentType());
                if(metadata.getContentDisposition()!=null)restoredMetadata.setContentDisposition(metadata.getContentDisposition());
                restoredMetadata.setUserMetadata(metadata.getUserMetadata());
                client.putObject(new PutObjectRequest(restoredBucket,key,file.toFile()).withMetadata(restoredMetadata).withCannedAcl(CannedAccessControlList.Private));
                try(var restored=client.getObject(restoredBucket,key)){assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes),MessageDigest.getInstance("SHA-256").digest(restored.getObjectContent().readAllBytes()));}
                var entry=new LinkedHashMap<String,Object>();
                entry.put("key",key);entry.put("bytes",bytes.length);entry.put("sha256",checksum);entry.put("file",file.getFileName().toString());
                entry.put("contentType",metadata.getContentType());entry.put("contentDisposition",metadata.getContentDisposition());entry.put("userMetadata",metadata.getUserMetadata());
                objects.add(entry);
            }
            assertFalse(objects.isEmpty());
            mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("objects-manifest.json").toFile(),Map.of("status","PASS","restoreBucket",restoredBucket,"objects",objects));
            mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("storage/evidence/coordinated-object-restore.json").toFile(),Map.of("status","PASS","restoredObjects",objects.size(),"restoreBucket",restoredBucket,"backupLocation","operator-selected protected filesystem root","offHostDurability","BLOCKED - physical independence not verified"));
        }finally{client.shutdown();}
    }
    @Test void realPolicyMetadataAndPrivateAcl() throws Exception {
        String endpoint=System.getenv().getOrDefault("RUSTFS_TEST_ENDPOINT", "http://127.0.0.1:19000");
        assertTrue(Set.of("http://127.0.0.1:19000","http://127.0.0.1:19010").contains(endpoint));
        var client=AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint,"us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(System.getenv("RUSTFS_ACCESS_KEY"),System.getenv("RUSTFS_SECRET_KEY"))))
                .withPathStyleAccessEnabled(true).build();
        String bucket="stage5d-policy-"+UUID.randomUUID();
        var result=new LinkedHashMap<String,Object>();
        try {
            client.createBucket(bucket);
            var metadata=new ObjectMetadata(); metadata.setContentLength(6); metadata.setContentType("text/plain");
            metadata.setContentDisposition("attachment; filename=diagnostic.txt"); metadata.addUserMetadata("diagnostic","preserved");
            client.putObject(new PutObjectRequest(bucket,"policy-denied",new ByteArrayInputStream("sample".getBytes()),metadata));
            var head=client.getObjectMetadata(bucket,"policy-denied"); assertEquals("preserved",head.getUserMetaDataOf("diagnostic"));
            assertEquals("attachment; filename=diagnostic.txt",head.getContentDisposition()); result.put("metadata","PASS");
            client.setBucketPolicy(bucket,"{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Deny\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::"+bucket+"/policy-denied\"}]}");
            assertNotNull(client.getBucketPolicy(bucket).getPolicyText());
            String signed=client.generatePresignedUrl(new GeneratePresignedUrlRequest(bucket,"policy-denied").withMethod(HttpMethod.GET).withExpiration(Date.from(Instant.now().plusSeconds(60)))).toString();
            var http=HttpClient.newHttpClient();
            assertEquals(403,http.send(HttpRequest.newBuilder(URI.create(signed)).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            client.deleteBucketPolicy(bucket); result.put("bucketPolicyEnforced","PASS");
            try {
                client.putObject(new PutObjectRequest(bucket,"acl-private",new ByteArrayInputStream("sample".getBytes()),metadata).withCannedAcl(CannedAccessControlList.Private));
                assertEquals(403,http.send(HttpRequest.newBuilder(URI.create(endpoint+"/"+bucket+"/acl-private")).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
                result.put("privateCannedAcl","PASS - accepted, anonymous GET denied");
            } catch(AmazonS3Exception error) { result.put("privateCannedAcl", "UNSUPPORTED HTTP "+error.getStatusCode()); }
            result.put("status","PASS");
        } finally {
            if(client.doesBucketExistV2(bucket)){client.deleteBucketPolicy(bucket);for(var object:client.listObjectsV2(bucket).getObjectSummaries())client.deleteObject(bucket,object.getKey());client.deleteBucket(bucket);}
            client.shutdown(); Files.createDirectories(Path.of("storage/evidence"));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("storage/evidence/policy-"+(endpoint.contains("19010")?"minio":"rustfs")+".json").toFile(),result);
        }
    }
    @Test void realSdkRoundtripSecurityRangeMultipartAndRestore() throws Exception {
        String endpoint = System.getenv().getOrDefault("RUSTFS_TEST_ENDPOINT", "http://127.0.0.1:19000");
        assertTrue(Set.of("http://127.0.0.1:19000", "http://zvonok-local-s3:9000", "http://127.0.0.1:19010", "http://zvonok-local-minio:9000").contains(endpoint));
        var client = AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(System.getenv("RUSTFS_ACCESS_KEY"), System.getenv("RUSTFS_SECRET_KEY"))))
                .withPathStyleAccessEnabled(true).withClientConfiguration(new ClientConfiguration().withMaxErrorRetry(1)
                        .withConnectionTimeout(3000).withSocketTimeout(10000)).build();
        String bucket = "stage5d-" + UUID.randomUUID();
        var results = new LinkedHashMap<String, Object>();
        results.put("endpoint", endpoint);
        Path backup = Files.createTempDirectory("zvonok-s3-restore-");
        try {
            if ("true".equals(System.getenv("RUSTFS_PREPARE_APP_BUCKET")) && !client.doesBucketExistV2("zvonok-stage5d-data")) {
                client.createBucket("zvonok-stage5d-data");
                var rule = new CORSRule().withAllowedOrigins(List.of("http://localhost:3000", "http://localhost:3001"))
                        .withAllowedMethods(List.of(CORSRule.AllowedMethods.GET, CORSRule.AllowedMethods.HEAD))
                        .withAllowedHeaders(List.of("Range", "If-Range"))
                        .withExposedHeaders(List.of("Content-Range", "Accept-Ranges", "Content-Length", "Content-Type", "ETag"));
                client.setBucketCrossOriginConfiguration("zvonok-stage5d-data",new BucketCrossOriginConfiguration(List.of(rule)));
            }
            client.createBucket(bucket);
            var service = new S3Service(client);
            ReflectionTestUtils.setField(service, "provider", endpoint.contains("19010") || endpoint.contains("minio") ? "minio" : "rustfs");
            ReflectionTestUtils.setField(service, "bucketName", bucket);
            ReflectionTestUtils.setField(service, "uploadExecutionTimeout", 30000);
            var http = HttpClient.newHttpClient();
            for (int size : new int[]{1048576, 5242880, 25545779, 50 * 1024 * 1024}) {
                byte[] bytes;
                if (size == 25545779) bytes = Files.readAllBytes(Path.of(System.getenv("RUSTFS_MP4")));
                else { bytes = new byte[size]; new Random(5).nextBytes(bytes); System.arraycopy(new byte[]{0,0,0,20,'f','t','y','p'}, 0, bytes, 0, 8); }
                assertEquals(size, bytes.length);
                String key = "diagnostic/" + size + ".mp4";
                long started = System.nanoTime();
                service.uploadFile(key, new ByteArrayInputStream(bytes), bytes.length, "video/mp4");
                long putMs = (System.nanoTime() - started) / 1_000_000;
                var head = client.getObjectMetadata(bucket, key);
                assertEquals(size, head.getContentLength()); assertEquals("video/mp4", head.getContentType());
                started = System.nanoTime();
                byte[] downloaded;
                try (var object = client.getObject(bucket, key)) { downloaded = object.getObjectContent().readAllBytes(); }
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes), MessageDigest.getInstance("SHA-256").digest(downloaded));
                long getMs = (System.nanoTime() - started) / 1_000_000;
                String signed = service.playbackUrl(key, "video/mp4", Instant.now().plusSeconds(60));
                var range = http.send(HttpRequest.newBuilder(URI.create(signed)).header("Range", "bytes=100-199").build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(206, range.statusCode()); assertArrayEquals(Arrays.copyOfRange(bytes,100,200), range.body());
                var invalid = http.send(HttpRequest.newBuilder(URI.create(signed)).header("Range", "bytes="+size+"-").build(), HttpResponse.BodyHandlers.discarding());
                assertEquals(416, invalid.statusCode());
                for (String suffix : new String[]{"/"+bucket+"/"+key, "/"+bucket+"?list-type=2"}) {
                    assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(endpoint+suffix)).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
                }
                assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(endpoint+"/"+bucket+"/unauthorized")).PUT(HttpRequest.BodyPublishers.ofString("denied")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
                if(size == 25545779) {
                    Path copy = backup.resolve("progressive.mp4"); Files.write(copy, downloaded);
                    client.deleteObject(bucket, key);
                    client.putObject(new PutObjectRequest(bucket,key,copy.toFile()));
                    try(var restored = client.getObject(bucket,key)) { assertArrayEquals(bytes, restored.getObjectContent().readAllBytes()); }
                    results.put("limitedObjectRestore", "PASS (host temp copy; not a database recovery test)");
                }
                results.put(Integer.toString(size), Map.of("putMs",putMs,"getMs",getMs,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),"status","PASS"));
            }
            String upload = client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket,"multipart")).getUploadId();
            var part = client.uploadPart(new UploadPartRequest().withBucketName(bucket).withKey("multipart").withUploadId(upload)
                    .withPartNumber(1).withPartSize(5*1024*1024).withInputStream(new ByteArrayInputStream(new byte[5*1024*1024])));
            client.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket,"multipart",upload,List.of(part.getPartETag())));
            assertEquals(5*1024*1024, client.getObjectMetadata(bucket,"multipart").getContentLength());
            String aborted = client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket,"abort")).getUploadId();
            client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket,"abort",aborted));
            assertTrue(client.listMultipartUploads(new ListMultipartUploadsRequest(bucket)).getMultipartUploads().isEmpty());
            results.put("multipartCompleteAndAbort", "PASS");
            assertEquals(5, client.listObjectsV2(bucket).getObjectSummaries().size());
            results.put("anonymousGetListPutDenied", "PASS"); results.put("range206And416", "PASS");
            results.put("status", "PASS");
        } finally {
            Files.createDirectories(Path.of("storage/evidence"));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("storage/evidence/sdk-compatibility-" + (endpoint.contains("19010") || endpoint.contains("minio") ? "minio" : "rustfs") + ".json").toFile(), results);
            if (client.doesBucketExistV2(bucket)) {
                for(var object : client.listObjectsV2(bucket).getObjectSummaries()) client.deleteObject(bucket,object.getKey());
                client.deleteBucket(bucket);
            }
            client.shutdown();
            try(var files = Files.list(backup)) { for(Path file : files.toList()) Files.delete(file); } Files.delete(backup);
        }
    }
}
