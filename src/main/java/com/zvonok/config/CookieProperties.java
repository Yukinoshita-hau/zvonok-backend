package com.zvonok.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties("security.cookie")
@Data
public class CookieProperties {
	private String sameSite;
	private boolean secure;
	private long maxAge;
	private String path;
}
