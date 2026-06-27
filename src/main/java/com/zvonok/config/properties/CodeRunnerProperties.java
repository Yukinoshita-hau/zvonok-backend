package com.zvonok.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "code-runner")
@Data
public class CodeRunnerProperties {

	private String url;
	private String apiKey;
}
