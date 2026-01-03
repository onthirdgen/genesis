package com.callaudit.notification.config;

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
    public OpenAPI notificationServiceAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8087");
        server.setDescription("Notification Service - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Notification Service API")
                .version("1.0.0")
                .description("Alert and notification management service. " +
                        "Sends notifications via email, Slack, and webhooks for compliance violations, " +
                        "high churn risk alerts, escalations, and review-required calls.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
