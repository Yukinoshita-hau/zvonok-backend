package com.zvonok.unittests.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.zvonok.config.S3Config;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;

class S3ClientConfigurationTest {
    @Test void boundsSlowAcknowledgementWithoutChangingPoolOrConnectTimeout() {
        var config = new S3Config();
        ReflectionTestUtils.setField(config, "endpoint", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(config, "accesKey", "test");
        ReflectionTestUtils.setField(config, "secretKey", "test");
        ReflectionTestUtils.setField(config, "region", "us-east-1");
        var client = (AmazonS3Client) config.amazonS3();
        try {
            assertEquals(180_000, client.getClientConfiguration().getSocketTimeout());
            assertEquals(10_000, client.getClientConfiguration().getConnectionTimeout());
            assertEquals(50, client.getClientConfiguration().getMaxConnections());
        } finally { client.shutdown(); }
    }
}
