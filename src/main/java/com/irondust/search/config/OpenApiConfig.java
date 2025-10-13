package com.irondust.search.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.PathItem;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        String adminKeyName = "adminKey";
        SecurityScheme adminKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("x-admin-key");

        return new OpenAPI()
                .info(new Info()
                        .title("IronDust Dropshipping API")
                        .version("0.1.0")
                        .description("Spring Boot WebFlux API for ingestion, search, content rendering, and admin operations."))
                .components(new Components().addSecuritySchemes(adminKeyName, adminKeyScheme));
    }

    @Bean
    public OpenApiCustomizer adminSecurityCustomizer() {
        return openAPI -> {
            if (openAPI.getPaths() == null) return;
            SecurityRequirement adminRequirement = new SecurityRequirement().addList("adminKey");

            openAPI.getPaths().forEach((path, item) -> {
                boolean pathRequiresAdmin =
                        path.startsWith("/ingest") ||
                        path.startsWith("/vectors") ||
                        path.startsWith("/admin/raw") ||
                        path.startsWith("/admin/blacklist") ||
                        false;

                if (pathRequiresAdmin) {
                    addRequirementToAllOperations(item, adminRequirement);
                }

                // Method-specific admin protection:
                if (path.startsWith("/feature-flags/")) {
                    Operation post = item.getPost();
                    if (post != null) post.addSecurityItem(adminRequirement);
                }
                if (path.startsWith("/admin/feature-flags/")) {
                    Operation patch = item.getPatch();
                    if (patch != null) patch.addSecurityItem(adminRequirement);
                }
            });
        };
    }

    private static void addRequirementToAllOperations(PathItem item, SecurityRequirement requirement) {
        if (item.getGet() != null) item.getGet().addSecurityItem(requirement);
        if (item.getPost() != null) item.getPost().addSecurityItem(requirement);
        if (item.getPut() != null) item.getPut().addSecurityItem(requirement);
        if (item.getPatch() != null) item.getPatch().addSecurityItem(requirement);
        if (item.getDelete() != null) item.getDelete().addSecurityItem(requirement);
        if (item.getHead() != null) item.getHead().addSecurityItem(requirement);
        if (item.getOptions() != null) item.getOptions().addSecurityItem(requirement);
        if (item.getTrace() != null) item.getTrace().addSecurityItem(requirement);
    }
}


