package com.keepr.ingestion;


import java.util.UUID;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.AbstractIntegrationTest;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import com.keepr.ingestion.service.ExtractionWorker;
import com.keepr.warranty.repository.WarrantyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Sprint 5: Intelligence Layer.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExtractionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private WarrantyRepository warrantyRepository;

    @Autowired
    private RawDocumentRepository rawDocumentRepository;

    @Autowired
    private ExtractionJobRepository extractionJobRepository;

    @MockitoBean
    private com.keepr.ingestion.service.OcrProvider ocrProvider;

    @Autowired
    private ExtractionWorker extractionWorker;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        extractionJobRepository.deleteAll();
        rawDocumentRepository.deleteAll();
        warrantyRepository.deleteAll();
        deviceRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM auth_otp");
    }

    @Test
    void extraction_successful_withFullData() throws Exception {
        String token = obtainJwt("9000000001");
        setupOcrMock(
                "KEEP INVOICE\n" +
                "Device: MacBook Pro\n" +
                "Brand: Apple\n" +
                "Model: M3 Max\n" +
                "Purchase Date: 2024-01-01\n" +
                "Warranty Start: 2024-01-01\n" +
                "Warranty End: 2025-01-01\n" +
                "Warranty Type: MANUFACTURER"
        );

        UUID jobId = uploadTestFile(token);
        extractionWorker.pollAndProcess();

        ExtractionJob job = extractionJobRepository.findById(requireNonNull(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getConfidenceScore()).isGreaterThanOrEqualTo(0.5);
        assertThat(job.getRawText()).contains("MacBook Pro");
        
        // Operational Metrics Verification
        assertThat(job.getExtractionVersion()).isEqualTo(1);
        assertThat(job.getOcrMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(job.getParseMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(job.getValidateMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(job.getTotalFieldsExtracted()).isEqualTo(8);
        assertThat(job.getSuccessfulFields()).isEqualTo(7); // product, brand, model, date, wStart, wEnd, wType
        
        // Confidence Breakdown Verification
        assertThat(job.getConfidenceBreakdown()).containsKey("product_name");
        assertThat(job.getConfidenceBreakdown().get("product_name")).isGreaterThan(0);
        assertThat(job.getConfidenceBreakdown()).containsKey("brand");
        
        // Extraction Snaphot Verification
        assertThat(job.getExtractionJson()).isNotEmpty();
        assertThat(job.getExtractionJson().get("productName")).isEqualTo("MacBook Pro");
        assertThat(job.getFailureReason()).isNull();

        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(warrantyRepository.count()).isEqualTo(1);
    }

    @Test
    void extraction_successful_deviceOnly_invalidWarranty() throws Exception {
        String token = obtainJwt("9000000002");
        // Warranty end is before start - validation should skip warranty but create device
        setupOcrMock(
                "KEEP INVOICE\n" +
                "Device: iPhone 15\n" +
                "Brand: Apple\n" +
                "Purchase Date: 2024-01-01\n" +
                "Warranty Start: 2024-01-01\n" +
                "Warranty End: 2023-01-01" 
        );

        UUID jobId = uploadTestFile(token);
        extractionWorker.pollAndProcess();

        ExtractionJob job = extractionJobRepository.findById(requireNonNull(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getExtractionJson()).isNotEmpty();
        assertThat(job.getFailureReason()).isNull();
        
        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(warrantyRepository.count()).isZero(); // Warranty skipped due to date inconsistency
    }

    @Test
    void extraction_failed_lowConfidence() throws Exception {
        String token = obtainJwt("9000000003");
        // Has Device: but nothing else, very low confidence
        setupOcrMock("Device: Pure Junk with no structure");

        UUID jobId = uploadTestFile(token);
        extractionWorker.pollAndProcess();

        ExtractionJob job = extractionJobRepository.findById(requireNonNull(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("Low extraction confidence");
        assertThat(job.getFailureReason()).isEqualTo("INVALID_DEVICE");
        
        // Verify metrics are captured even on failure
        assertThat(job.getOcrMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(job.getParseMs()).isNotNull().isGreaterThanOrEqualTo(0);
        
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void extraction_failed_missingMandatoryProductName() throws Exception {
        String token = obtainJwt("9000000004");
        // Has other info but missing 'Device:'
        setupOcrMock(
                "Brand: Apple\n" +
                "Purchase Date: 2024-01-01"
        );

        UUID jobId = uploadTestFile(token);
        extractionWorker.pollAndProcess();

        ExtractionJob job = extractionJobRepository.findById(requireNonNull(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("Missing mandatory field: productName");
        assertThat(job.getFailureReason()).isEqualTo("INVALID_DEVICE");
    }

    private void setupOcrMock(String text) {
        when(ocrProvider.extractText(anyString())).thenReturn(text);
    }

    private UUID uploadTestFile(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.5 content".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText());
    }

    private String obtainJwt(String phoneNumber) throws Exception {
        mockMvc.perform(post(requireNonNull("/auth/send-otp"))
                .contentType(requireNonNull(MediaType.APPLICATION_JSON))
                .content(requireNonNull(String.format("{\"phoneNumber\": \"%s\"}", phoneNumber))));

        String code = requireNonNull(jdbcTemplate.queryForObject(
                "SELECT otp_code FROM auth_otp WHERE phone_number = ? ORDER BY expires_at DESC LIMIT 1",
                String.class, phoneNumber));

        MvcResult verifyResult = mockMvc.perform(post(requireNonNull("/auth/verify-otp"))
                .contentType(requireNonNull(MediaType.APPLICATION_JSON))
                .content(requireNonNull(String.format("{\"phoneNumber\": \"%s\", \"otpCode\": \"%s\"}", phoneNumber, code))))
                .andReturn();

        return requireNonNull(objectMapper.readTree(verifyResult.getResponse().getContentAsString()).get("accessToken")).asText();
    }

    // Helper to avoid static import conflicts
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String url) {
        return requireNonNull(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(requireNonNull(url)));
    }
}
