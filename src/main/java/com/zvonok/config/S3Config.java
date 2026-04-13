package com.zvonok.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
public class S3Config {
	@Value("${s3.endpoint}")
	private String endpoint;

	@Value("${s3.access-key}")
	private String accesKey;

	@Value("${s3.secret-access-key}")
	private String secretKey;

	@Value("${s3.region:ru-central-1}")
	private String region;

	@Bean
	public AmazonS3 amazonS3() {
		return AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(
						new AwsClientBuilder.EndpointConfiguration(endpoint, region))
				.withCredentials(new AWSStaticCredentialsProvider(
						new BasicAWSCredentials(accesKey, secretKey)))
				.withPathStyleAccessEnabled(true).build();
	}
}
