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

    @Autowired
    private ExtractionJobRepository extractionJobRepository;

    @SpyBean
    private com.keepr.ingestion.service.IngestionFailureService ingestionFailureService;

    @Autowired
    private ExtractionWorker extractionWorker;

    @BeforeEach
    void cleanDb() {
        extractionJobRepository.deleteAll();
        rawDocumentRepository.deleteAll();
        warrantyRepository.deleteAll();
        deviceRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM auth_otp");
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
    }

    @Test
    void uploadDocument_mimeSpoofed_rejected() throws Exception {
        String token = obtainJwt("9999999998");
        // Spoof: .pdf extension and content-type, but actual content is a shell script/plain text
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "#!/bin/bash\necho 'malicious'".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(rawDocumentRepository.count()).isZero();
    }

    @Test
    void uploadDocument_transactionRollback_onJobFailure() throws Exception {
        String token = obtainJwt("9876543210");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rollback-test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 dummy content".getBytes() // Valid PDF magic bytes for Tika
        );

        // Force ExtractionJobRepository.save to fail
        Mockito.doThrow(new RuntimeException("DB Failure"))
               .when(extractionJobRepository).save(org.mockito.ArgumentMatchers.any(com.keepr.ingestion.model.ExtractionJob.class));

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        // Verify TRANSACTION WORKS: RawDocument must NOT be saved even though it is saved before the job
        assertThat(rawDocumentRepository.count()).isZero();
        assertThat(extractionJobRepository.count()).isZero();

        Mockito.reset(extractionJobRepository);
    }

    @Test
    void handleFailure_terminalState_idempotent() throws Exception {
        String token = obtainJwt("9999998888");
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.5 content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andReturn();
        
        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());

        // Mark as COMPLETED manually
        var job = extractionJobRepository.findById(jobId).orElseThrow();
        job.setStatus(com.keepr.ingestion.model.JobStatus.COMPLETED);
        extractionJobRepository.saveAndFlush(job);

        // Try to trigger failure handling
        ingestionFailureService.handleFailure(jobId, new RuntimeException("Late error"));

        // Verify it's STILL COMPLETED (idempotency guard worked)
        var updatedJob = extractionJobRepository.findById(jobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo(com.keepr.ingestion.model.JobStatus.COMPLETED);
        assertThat(updatedJob.getRetryCount()).isZero();
    }

    @Test
    void handleFailure_maxRetries_transitionsToFailed() throws Exception {
        String token = obtainJwt("9999997777");
        MockMultipartFile file = new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-1.5 content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andReturn();
        
        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());

        // Manually set retryCount to 2
        var job = extractionJobRepository.findById(jobId).orElseThrow();
        job.setRetryCount(2);
        extractionJobRepository.saveAndFlush(job);

        // Trigger 3rd failure
        ingestionFailureService.handleFailure(jobId, new RuntimeException("Final fail"));

        // Verify it transitioned to FAILED
        var finalJob = extractionJobRepository.findById(jobId).orElseThrow();
        assertThat(finalJob.getStatus()).isEqualTo(com.keepr.ingestion.model.JobStatus.FAILED);
        assertThat(finalJob.getRetryCount()).isEqualTo(3);
    }

    @Test
    void getJobStatus_success_scopedToHousehold() throws Exception {
        String householdAToken = obtainJwt("1111111111");
        String householdBToken = obtainJwt("2222222222");

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.5 content".getBytes());

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
        MockMultipartFile file = new MockMultipartFile("file", "warranty.jpg", "image/jpeg", 
                new byte[]{ (byte)0xff, (byte)0xd8, (byte)0xff, (byte)0xe0, 0, 0x10, 'J', 'F', 'I', 'F', 0 }); // JPEG magic bytes

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andReturn();

        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());

        // Manually trigger worker polling
        extractionWorker.pollAndProcess();

        // Verify status
        mockMvc.perform(get("/api/v1/documents/jobs/" + jobId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify entities (stubs should create Macbook Pro + Warranty)
        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(warrantyRepository.count()).isEqualTo(1);
        
        var device = deviceRepository.findAll().get(0);
        assertThat(device.getName()).isEqualTo("macbook pro"); // Verified normalized lowercase
    }

    @Test
    void workerFlow_idempotency_avoidsDuplicateDevices() throws Exception {
        String token = obtainJwt("7777777777");
        MockMultipartFile file = new MockMultipartFile("file", "inv.pdf", "application/pdf", "%PDF-1.5 content".getBytes());

        // Upload twice
        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).header("Authorization", "Bearer " + token));
        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).header("Authorization", "Bearer " + token));

        // Process all jobs
        extractionWorker.pollAndProcess();
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

        // Get OTP from DB using JdbcTemplate
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
