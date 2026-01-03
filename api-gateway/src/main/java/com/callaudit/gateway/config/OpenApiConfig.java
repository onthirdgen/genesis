package com.callaudit.gateway.config;

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
    public OpenAPI apiGatewayAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8080");
        server.setDescription("API Gateway - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Call Auditing Platform - API Gateway")
                .version("1.0.0")
                .description("Central API Gateway for the Call Auditing Platform. " +
                        "Routes requests to microservices with circuit breaker patterns, rate limiting, " +
                        "and retry logic for resilience.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
