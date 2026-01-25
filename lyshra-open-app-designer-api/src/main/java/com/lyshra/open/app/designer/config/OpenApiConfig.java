package com.lyshra.open.app.designer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Workflow Designer API.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI workflowDesignerOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(securityComponents())
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("Lyshra OpenApp Workflow Designer API")
                .version("1.0.0")
                .description("""
                        REST API for the Lyshra OpenApp Workflow Designer.

                        This API provides endpoints for:
                        - **Workflow Management**: Create, read, update, delete workflow definitions
                        - **Version Control**: Manage workflow versions with activation, deprecation, and rollback
                        - **Execution Monitoring**: Start, monitor, cancel, and retry workflow executions
                        - **Processor Metadata**: Retrieve available processors for the visual designer
                        - **Validation**: Validate workflow configurations before deployment
                        - **Schema Access**: Get JSON schemas for client-side validation

                        ## Authentication

                        All API endpoints (except `/api/v1/auth/**` and `/api/v1/health`) require JWT authentication.

                        1. Obtain a token via `POST /api/v1/auth/login`
                        2. Include the token in the `Authorization` header: `Bearer <token>`

                        ## Rate Limiting

                        API calls are subject to rate limiting. Current limits:
                        - 1000 requests per minute per user
                        - 100 workflow executions per minute per user
                        """)
                .contact(new Contact()
                        .name("Lyshra OpenApp Team")
                        .email("support@lyshra.com")
                        .url("https://lyshra.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Development Server"),
                new Server()
                        .url("https://api.lyshra.com")
                        .description("Production Server")
        );
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from /api/v1/auth/login"));
    }
}
