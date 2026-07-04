package com.shopflow.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI shopFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopFlow API")
                        .version("v1")
                        .description("REST API for the ShopFlow portfolio e-commerce system."))
                .tags(List.of(
                        new Tag().name("Authentication").description("Public registration and login."),
                        new Tag().name("Catalog").description("Public category and active product queries."),
                        new Tag().name("Cart").description("Requires CUSTOMER role."),
                        new Tag().name("Customer Orders").description("Requires CUSTOMER role."),
                        new Tag().name("Admin Products").description("Requires ADMIN role."),
                        new Tag().name("Admin Orders").description("Requires ADMIN role."),
                        new Tag().name("Health").description("Public application health check.")
                ))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("30-minute HS256 access token with sub, userId, role, iat and exp claims.")
                ))
                .paths(new Paths().addPathItem("/actuator/health", healthPath()));
    }

    private PathItem healthPath() {
        Schema<?> schema = new ObjectSchema()
                .addProperty("status", new StringSchema().example("UP"));
        Content content = new Content().addMediaType(
                "application/vnd.spring-boot.actuator.v3+json",
                new MediaType().schema(schema)
        );
        return new PathItem().get(new Operation()
                .tags(List.of("Health"))
                .summary("Get application health")
                .description("Public endpoint. Only summary health information is returned.")
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Application is healthy").content(content))
                        .addApiResponse("503", new ApiResponse().description("Application is unhealthy").content(content))));
    }
}
