package com.keepr.ingestion;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.AbstractIntegrationTest;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.service.ExtractionWorker;
import com.keepr.warranty.repository.WarrantyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Sprint 4: Document Ingestion and Async Extraction.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IngestionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private WarrantyRepository warrantyRepository;

    @Autowired
    private RawDocumentRepository rawDocumentRepository;

    @SpyBean
    private ExtractionJobRepository extractionJobRepository;

    @Autowired
    private ExtractionWorker extractionWorker;

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
    void uploadDocument_transactionRollback_onJobFailure() throws Exception {
        String token = obtainJwt("9876543210");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rollback-test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fail".getBytes()
        );

        // Force ExtractionJobRepository.save to fail
        Mockito.doThrow(new RuntimeException("DB Failure"))
               .when(extractionJobRepository).save(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        // Verify TRANSACTION WORKS: RawDocument must NOT be saved even though it is saved before the job
        assertThat(rawDocumentRepository.count()).isZero();
        assertThat(extractionJobRepository.count()).isZero();

        // Step 4: Cleanup Spy
        Mockito.reset(extractionJobRepository);
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

        assertThat(device.getName()).isEqualTo("macbook pro");
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
        mockMvc.perform(post("/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"" + phoneNumber + "\"}"))
                .andExpect(status().isOk());

        // Get OTP from DB using JdbcTemplate since the backend still uses Postgres for OTPs
        String code = jdbcTemplate.queryForObject(
                "SELECT otp_code FROM auth_otp WHERE phone_number = ? ORDER BY expires_at DESC LIMIT 1",
                String.class, phoneNumber);
        assertThat(code).isNotNull();

        // Step 3: Verify OTP
        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"phoneNumber\": \"%s\", \"otpCode\": \"%s\"}", phoneNumber, code)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        return responseNode.get("accessToken").asText();
    }
}
