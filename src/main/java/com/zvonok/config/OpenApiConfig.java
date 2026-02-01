package com.zvonok.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
		info = @Info(title = "Zvonok - платформа для обмена информацией в реальном времене",
				description = "Api платформы zvonok", version = "0.0.1",
				contact = @Contact(name = "Mirzuev Rashid", email = "mirzuevRashid@yandex.ru",
						url = "none")))
@SecurityScheme(name = "JWT", type = SecuritySchemeType.HTTP, bearerFormat = "JWT",
		scheme = "bearer")
public class OpenApiConfig {
	// Конфиг для свагера (Swagger)
	
}
