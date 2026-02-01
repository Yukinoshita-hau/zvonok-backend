package com.zvonok.documentation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.exception_handler.JsonErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(responseCode = "400", description = "",
		content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = JsonErrorResponse.class)))
public @interface ApiResponse400 {

	@AliasFor(annotation = ApiResponse.class, attribute = "description")
	String message() default CommonApiDescriptions.VALIDATION_FAILED;

}
