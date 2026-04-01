package com.keepr.ingestion;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.service.ExtractionWorker;
import com.keepr.warranty.repository.WarrantyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Sprint 4: Document Ingestion and Async Extraction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class IngestionIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("keepr_test")
                    .withUsername("keepr")
                    .withPassword("keepr_test");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(REDIS_PORT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private WarrantyRepository warrantyRepository;

    @Autowired
    private RawDocumentRepository rawDocumentRepository;

    @Autowired
    private ExtractionJobRepository extractionJobRepository;

    @Autowired
    private ExtractionWorker extractionWorker;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @BeforeEach
    void cleanDb() {
        extractionJobRepository.deleteAll();
        rawDocumentRepository.deleteAll();
        warrantyRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void uploadDocument_success_createsJob() throws Exception {
        String token = obtainJwt("9999999999");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(rawDocumentRepository.count()).isEqualTo(1);
        assertThat(extractionJobRepository.count()).isEqualTo(1);
    }

    @Test
    void getJobStatus_success_scopedToHousehold() throws Exception {
        String householdAToken = obtainJwt("1111111111");
        String householdBToken = obtainJwt("2222222222");

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        // Household A uploads
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + householdAToken))
                .andReturn();
        
        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());

        // Household A can see it
        mockMvc.perform(get("/api/v1/documents/jobs/" + jobId)
                .header("Authorization", "Bearer " + householdAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));

        // Household B cannot see it
        mockMvc.perform(get("/api/v1/documents/jobs/" + jobId)
                .header("Authorization", "Bearer " + householdBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void workerFlow_successfulProcessing_createsEntities() throws Exception {
        String token = obtainJwt("8888888888");
        MockMultipartFile file = new MockMultipartFile("file", "warranty.jpg", "image/jpeg", "image-content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andReturn();

        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());

        // Manually trigger worker polling instead of waiting for scheduler
        extractionWorker.pollAndProcess();

        // Verify status
        mockMvc.perform(get("/api/v1/documents/jobs/" + jobId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify entities (stubs should create MacBook Pro + Warranty)
        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(warrantyRepository.count()).isEqualTo(1);
        
        var device = deviceRepository.findAll().get(0);
        var warranty = warrantyRepository.findAll().get(0);

        assertThat(device.getName()).isEqualTo("MacBook Pro");
        assertThat(warranty.getDeviceId()).isEqualTo(device.getId());
        assertThat(warranty.getStartDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void workerFlow_idempotency_avoidsDuplicateDevices() throws Exception {
        String token = obtainJwt("7777777777");
        MockMultipartFile file = new MockMultipartFile("file", "inv.pdf", "application/pdf", "c".getBytes());

        // Upload twice
        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).header("Authorization", "Bearer " + token));
        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).header("Authorization", "Bearer " + token));

        // Process all jobs
        extractionWorker.pollAndProcess();

        // Should have 1 device and 1 warranty because of idempotency (stubs return identical data)
        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(warrantyRepository.count()).isEqualTo(1);
    }

    private String obtainJwt(String phoneNumber) throws Exception {
        // Step 1: Send OTP
        mockMvc.perform(post("/api/v1/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"" + phoneNumber + "\"}"))
                .andExpect(status().isOk());

        // Step 2: Get OTP from Redis (Sprint 2 implementation)
        String code = stringRedisTemplate.opsForValue().get("otp:" + phoneNumber);
        assertThat(code).isNotNull();

        // Step 3: Verify OTP
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"phoneNumber\": \"%s\", \"otpCode\": \"%s\"}", phoneNumber, code)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        return responseNode.get("token").asText();
    }
}
