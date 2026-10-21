package com.zvonok.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
@Slf4j
public class S3Config {
	@Value("${s3.endpoint}")
	private String endpoint;

	@Value("${s3.access-key}")
	private String accesKey;

	@Value("${s3.secret-access-key}")
	private String secretKey;

	@Value("${s3.region:ru-central-1}")
	private String region;

	@Value("${s3.signing-endpoint:${s3.endpoint}}")
	private String signingEndpoint;
	@Value("${s3.path-style:true}")
	private boolean pathStyle = true;
	@Value("${s3.connect-timeout-ms:10000}")
	private int connectTimeout = 10_000;
	@Value("${s3.socket-timeout-ms:180000}")
	private int socketTimeout = 180_000;
	@Value("${s3.max-retries:3}")
	private int maxRetries = 3;

	@Bean
	@Primary
	public AmazonS3 amazonS3() {
		AmazonS3Client client = (AmazonS3Client) AmazonS3ClientBuilder.standard()
				// The measured S3 acknowledgement for 25 MiB exceeds the SDK's 50-second read timeout.
				.withClientConfiguration(new ClientConfiguration().withSocketTimeout(socketTimeout)
						.withConnectionTimeout(connectTimeout).withMaxErrorRetry(maxRetries))
				.withEndpointConfiguration(
						new AwsClientBuilder.EndpointConfiguration(endpoint, region))
				.withCredentials(new AWSStaticCredentialsProvider(
						new BasicAWSCredentials(accesKey, secretKey)))
				.withPathStyleAccessEnabled(pathStyle).build();
        ClientConfiguration config = client.getClientConfiguration();
        log.info("s3_client sdk=1.12.700 transport=ApacheSync max_connections={} connect_ms={} socket_ms={} request_ms={} client_ms={} max_error_retry={} policy_retries={} retry_mode={} expect_continue={} proxy_configured={}",
                config.getMaxConnections(), config.getConnectionTimeout(), config.getSocketTimeout(), config.getRequestTimeout(),
                config.getClientExecutionTimeout(), config.getMaxErrorRetry(), config.getRetryPolicy().getMaxErrorRetry(),
                config.getRetryMode(), config.isUseExpectContinue(), config.getProxyHost() != null);
        return client;
	}

	@Bean("s3SigningClient")
	public AmazonS3 s3SigningClient() {
		return AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(signingEndpoint, region))
				.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accesKey, secretKey)))
				.withPathStyleAccessEnabled(pathStyle).build();
	}
}
