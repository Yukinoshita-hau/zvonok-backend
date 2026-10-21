package com.zvonok.unittests.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.sun.net.httpserver.HttpServer;
import com.zvonok.service.S3Service;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class S3UploadDiagnosticsTest {
    @Test void realSdkFailureDeletesStagedFileWithoutChangingTimeoutOrRetrySemantics() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            byte[] body = "<Error><Code>InternalError</Code><Message>controlled failure</Message></Error>".getBytes();
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var client = AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                "http://127.0.0.1:" + server.getAddress().getPort(), "ru-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("diagnostic", "diagnostic")))
                .withPathStyleAccessEnabled(true).withClientConfiguration(new ClientConfiguration().withMaxErrorRetry(0)
                        .withConnectionTimeout(1000).withSocketTimeout(1000)).build();
        Set<Path> before = stagedFiles();
        try {
            byte[] mp4 = new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m',0,0,0,0,'m','p','4','2'};
            assertThrows(RuntimeException.class, () -> new S3Service(client).uploadFile("diagnostic-only", new ByteArrayInputStream(mp4), mp4.length, "video/mp4"));
            assertEquals(before, stagedFiles());
        } finally { client.shutdown(); server.stop(0); }
    }

    private Set<Path> stagedFiles() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(p -> p.getFileName().toString().startsWith("zvonok-upload-")).collect(Collectors.toSet());
        }
    }
}
