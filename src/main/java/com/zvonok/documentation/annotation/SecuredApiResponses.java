package com.zvonok.documentation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.exception_handler.JsonErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses(value = {@ApiResponse(responseCode = "401",
		description = CommonApiDescriptions.AUTOCEIFICATION_FAILED,
		content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = JsonErrorResponse.class),
				examples = @ExampleObject(name = "AuthError", value = """
							{
								"message": "JWT token not valid or missing!",
								"status": 401
							}
						""")))

})
public @interface SecuredApiResponses {

}
