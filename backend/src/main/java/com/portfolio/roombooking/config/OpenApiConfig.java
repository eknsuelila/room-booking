package com.portfolio.roombooking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI documentation.
 *
 * <p>Swagger UI lives at {@code /swagger-ui.html}. To try protected endpoints: call
 * {@code POST /api/auth/login}, copy the {@code token}, click "Authorize" and paste it.
 * The {@code @SecurityScheme} below is what makes that "Authorize" button send
 * {@code Authorization: Bearer <token>}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Room Booking API", version = "1.0",
                description = "Book meeting rooms without double bookings. Roles: USER and ADMIN."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
