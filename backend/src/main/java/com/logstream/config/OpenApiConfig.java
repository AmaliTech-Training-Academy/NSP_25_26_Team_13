package com.logstream.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "LogStream API",
                version = "v1",
                description = "LogStream provides centralized log ingestion, search, analytics, and retention management.",
                contact = @Contact(
                        name = "LogStream Team"
                ),
                license = @License(name = "MIT")
        )
)
public class OpenApiConfig {

    @Bean
    public OpenAPI logstreamOpenAPI() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("LogStream API")
                        .version("v1")
                        .description("Centralized logging, search, analytics, and retention management API."))
                .externalDocs(new ExternalDocumentation()
                        .description("Project documentation")
                        .url("https://example.com/docs"));
    }
}

