package com.callaudit.voc.config;

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
    public OpenAPI vocServiceAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8084");
        server.setDescription("VoC Service - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Voice of Customer (VoC) Service API")
                .version("1.0.0")
                .description("Extract insights, themes, keywords, and actionable intelligence from call transcriptions. " +
                        "This service analyzes customer intent, sentiment patterns, churn risk predictions, " +
                        "and provides aggregated trends and metrics for Voice of Customer analytics.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
