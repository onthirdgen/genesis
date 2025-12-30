package com.callaudit.ingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI callIngestionServiceAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8081");
        server.setDescription("Call Ingestion Service - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Call Ingestion Service API")
                .version("1.0.0")
                .description("Service for ingesting and storing call audio files. " +
                        "This service handles audio file uploads, stores them in MinIO (S3-compatible storage), " +
                        "persists call metadata to PostgreSQL, and publishes CallReceived events to Kafka for downstream processing.")
                .contact(contact);

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
