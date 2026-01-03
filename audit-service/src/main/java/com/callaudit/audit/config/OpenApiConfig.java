package com.callaudit.audit.config;

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
    public OpenAPI auditServiceAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8085");
        server.setDescription("Audit Service - Development");

        Contact contact = new Contact();
        contact.setName("Call Auditing Platform Team");

        Info info = new Info()
                .title("Audit Service API")
                .version("1.0.0")
                .description("Compliance evaluation and quality scoring service. " +
                        "Evaluates calls against configurable compliance rules, assigns quality scores, " +
                        "detects violations, and generates audit reports.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
