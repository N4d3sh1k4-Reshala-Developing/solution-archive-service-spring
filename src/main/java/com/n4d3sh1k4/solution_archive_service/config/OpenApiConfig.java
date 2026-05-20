package com.n4d3sh1k4.solution_archive_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import java.util.List;

@Configuration
public class OpenApiConfig {



    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reshala Solution-Archive-Service API")
                        .description("Recognition or CAS orchestration service")
                        .version("1.0.1")
                        .contact(new Contact()
                                .name("Mihail Krivosheev")
                                .url("https://github.com/NEXUSPROGECT")))

                .servers(List.of(
                        new Server()
                                .url("https://api.reshala.n4d3sh1k4.site/api/v0")
                                .description("Production"),
                        new Server()
                                .url("http://localhost:8180/api/v0")
                                .description("Local Environment")
                ))

                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
