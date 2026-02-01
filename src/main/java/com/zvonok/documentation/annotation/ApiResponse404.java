package com.zvonok.documentation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import com.zvonok.exception_handler.JsonErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(responseCode = "404", description = "",
		content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = JsonErrorResponse.class),
				examples = @ExampleObject(name = "NotFound", value = """
							{
								"message": "User not found",
								"status": 404
							}
						""")))
public @interface ApiResponse404 {

	@AliasFor(annotation = ApiResponse.class, attribute = "description")
	String description() default "Not Found";

}
