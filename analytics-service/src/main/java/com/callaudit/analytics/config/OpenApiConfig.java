package com.callaudit.analytics.config;

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
    public OpenAPI analyticsServiceAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8086");
        server.setDescription("Analytics Service - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Analytics Service API")
                .version("1.0.0")
                .description("Real-time analytics, metrics aggregation, and dashboard data service. " +
                        "Provides KPIs, trends, agent performance metrics, customer satisfaction scores, " +
                        "and compliance summaries.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
