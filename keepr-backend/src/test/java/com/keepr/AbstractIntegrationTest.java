package com.keepr;

import java.time.Duration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all integration tests in Project Keepr.
 * 
 * Correctly uses the Singleton Container pattern via the framework-managed lifecycle
 * in JUnit 5. Containers are started once per JVM session and shared across all subclasses.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Prevents state leakage
public abstract class AbstractIntegrationTest {

    static {
        // Logging used to confirm singleton behavior and improve observability
        System.out.println("\n🚀 Starting shared Testcontainers for integration tests...\n");
    }

    @Container
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("keepr_test")
                    .withUsername("keepr")
                    .withPassword("keepr_test")
                    .withLabel("testcontainer", "keepr-postgres")
                    .withReuse(true)
                    .withStartupTimeout(Duration.ofSeconds(60));

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(6379)
                    .withLabel("testcontainer", "keepr-redis")
                    .withReuse(true)
                    .withStartupTimeout(Duration.ofSeconds(60));

    /**
     * Centralized dynamic property injection for Testcontainers.
     * Use spring.data.redis.* to align with Spring Boot 3 standards.
     * 
     * @param registry the dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
