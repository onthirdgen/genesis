package com.callaudit.ingestion.contract;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for contract tests that verify integration with REAL infrastructure.
 *
 * This class:
 * - Starts PostgreSQL (TimescaleDB), MinIO, and Kafka containers via Testcontainers
 * - Configures Spring Boot to connect to these containers
 * - Is tagged with @Tag("contract") for selective test execution
 *
 * Contract tests are excluded from CI/CD by default and only run locally via:
 * mvn verify -Pintegration
 *
 * All contract tests should extend this class.
 */
@SpringBootTest
@ActiveProfiles("contract")
@Testcontainers
@Tag("contract")
public abstract class ContractTestBase {

    // PostgreSQL container with TimescaleDB extension
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("call_auditing")
     .withUsername("postgres")
     .withPassword("postgres");

    // MinIO container for S3-compatible storage
    @Container
    static GenericContainer<?> minio = new GenericContainer<>(
        DockerImageName.parse("minio/minio:latest")
    ).withExposedPorts(9000)
     .withEnv("MINIO_ROOT_USER", "minioadmin")
     .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
     .withCommand("server", "/data");

    // Kafka container
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    /**
     * Configure Spring Boot application properties to connect to Testcontainers.
     *
     * This method overrides application.yml properties with container-specific values.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // MinIO
        registry.add("minio.endpoint", () ->
            "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
