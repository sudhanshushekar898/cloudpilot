package dev.cloudpilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description generated from the controllers (springdoc). Swagger UI is at
 * /swagger-ui.html: log in with POST /api/v1/auth/login, then use "Authorize" with the token.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI cloudPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudPilot API")
                        .version("v1")
                        .description("Cloud monitoring and incident management with evidence-backed AI analysis. "
                                + "Errors use RFC 9457 problem details.")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login")))
                // Every endpoint needs a token except the few that SecurityConfig permits.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
