package com.zvonok.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.documentation.CommonControllerApiDescriptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Common", description = "Служебные (health/monitoring) эндпоинты")
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class CommonController {

	@Operation(summary = "Health check",
			description = "Проверка доступности сервиса. Возвращает статус UP.")
	@ApiResponse(responseCode = "200",
			description = CommonControllerApiDescriptions.COMMON_HEALTH_CHECK_SUCCESS)
	@GetMapping("/health")
	public String healthCheck() {
		return "{\"status\":\"UP\"}";
	}
}
