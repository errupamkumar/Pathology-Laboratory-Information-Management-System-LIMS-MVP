package com.lims.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pathology Laboratory Information Management System (LIMS) API")
                        .version("1.0.0")
                        .description("""
                                Enterprise Modular Monolith Pathology LIMS.
                                Built with Java 21, Spring Boot 3.3, Spring Modulith, Flyway and Zero-Click Silent Hardware Printing.

                                **Authentication:** call `POST /api/v1/auth/login`, then click *Authorize* and paste the `accessToken`.
                                **Errors** are RFC 7807 `application/problem+json` documents carrying a `correlationId`.
                                **Lists** are paged with `page`, `size` (max 100) and `sort` parameters.
                                """)
                        .contact(new Contact()
                                .name("Healthcare Architecture Group")
                                .email("architect@lims.healthcare.com"))
                        .license(new License().name("Proprietary Medical Software")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
