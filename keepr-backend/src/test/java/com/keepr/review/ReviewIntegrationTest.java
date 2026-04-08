package com.keepr.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.AbstractIntegrationTest;
import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import com.keepr.review.dto.ConfirmReviewRequest;
import com.keepr.review.model.ReviewTask;
import com.keepr.review.model.ReviewTaskStatus;
import com.keepr.review.repository.ReviewTaskRepository;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.repository.WarrantyRepository;
import com.keepr.auth.model.User;
import com.keepr.auth.repository.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static java.util.Objects.requireNonNull;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ExtractionJobRepository extractionJobRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private WarrantyRepository warrantyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RawDocumentRepository rawDocumentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_PHONE = "9000000005";

    private UUID householdId;
    private ExtractionJob testJob;
    private ReviewTask testTask;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = obtainJwt(TEST_PHONE);
        
        User user = userRepository.findByPhoneNumber(TEST_PHONE).orElseThrow();
        householdId = jdbcTemplate.queryForObject(
                "SELECT household_id FROM household_members WHERE user_id = ? LIMIT 1",
                UUID.class, user.getId());

        // Need a Raw Document to satisfy foreign key
        RawDocument doc = new RawDocument();
        doc.setId(UUID.randomUUID());
        doc.setHouseholdId(householdId);
        doc.setUploadedBy(user.getId());
        doc.setFileName("dummy.pdf");
        doc.setFileUrl("http://s3.amazonaws.com/dummy.pdf");
        doc.setFileType("application/pdf");
        rawDocumentRepository.save(doc);

        // Needs an Extraction Job
        ExtractionJob job = new ExtractionJob();
        job.setId(UUID.randomUUID());
        job.setHouseholdId(householdId);
        job.setRawDocumentId(doc.getId()); 
        job.setStatus(JobStatus.REVIEW_REQUIRED);
        job.setExtractionVersion(1);
        testJob = extractionJobRepository.save(job);

        // Needs a pending Review Task
        ReviewTask task = new ReviewTask();
        task.setJobId(testJob.getId());
        task.setHouseholdId(householdId);
        task.setRawText("raw text");
        task.setExtractionJson(Map.of("productName", "Test Product"));
        task.setStatus(ReviewTaskStatus.PENDING);
        testTask = reviewTaskRepository.save(task);
    }

    @AfterEach
    void tearDown() {
        reviewTaskRepository.deleteAll();
        extractionJobRepository.deleteAll();
        rawDocumentRepository.deleteAll();
        deviceRepository.deleteAll();
        warrantyRepository.deleteAll();
    }

    @Test
    void getPendingTasks_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/review/tasks")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(testTask.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getTask_shouldReturnDetails() throws Exception {
        mockMvc.perform(get("/api/v1/review/tasks/" + testTask.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testTask.getId().toString()))
                .andExpect(jsonPath("$.rawText").value("raw text"))
                .andExpect(jsonPath("$.extractionJson.productName").value("Test Product"));
    }

    @Test
    void confirmTask_shouldCreateDeviceAndWarranty_andMarkTaskCompleted() throws Exception {
        CreateDeviceRequest deviceReq = new CreateDeviceRequest("test device confirmed", "brand", "model", "LAPTOP", LocalDate.now());
        CreateWarrantyRequest warrantyReq = new CreateWarrantyRequest(null, "MANUFACTURER", LocalDate.now(), LocalDate.now().plusYears(1));
        ConfirmReviewRequest request = new ConfirmReviewRequest(deviceReq, warrantyReq);

        mockMvc.perform(post("/api/v1/review/tasks/" + testTask.getId() + "/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert Task updated
        ReviewTask taskInDb = reviewTaskRepository.findByHouseholdIdAndJobId(householdId, 
                java.util.Objects.requireNonNull(testJob.getId())).orElseThrow();
        assertThat(taskInDb.getStatus()).isEqualTo(ReviewTaskStatus.COMPLETED);

        // Assert Job updated
        ExtractionJob jobInDb = extractionJobRepository.findById(Objects.requireNonNull(testJob.getId())).orElseThrow();
        assertThat(jobInDb.getStatus()).isEqualTo(JobStatus.USER_CONFIRMED);

        // Assert Device Created
        var devices = deviceRepository.findAll();
        assertThat(devices).hasSize(1);
        var device = devices.get(0);
        assertThat(device.getName()).isEqualTo("test device confirmed");
        assertThat(device.getBrand()).isEqualTo("brand");
        assertThat(device.getModel()).isEqualTo("model");

        // Assert Warranty Created
        var warranties = warrantyRepository.findAll();
        assertThat(warranties).hasSize(1);
        assertThat(warranties.get(0).getDeviceId()).isEqualTo(device.getId());
    }
    
    @Test
    void confirmTask_alreadyProcessed_shouldReturn409() throws Exception {
        testTask.setStatus(ReviewTaskStatus.COMPLETED);
        reviewTaskRepository.save(testTask);
        
        CreateDeviceRequest deviceReq = new CreateDeviceRequest("Test Device", "Brand", null, "OTHER", null);
        ConfirmReviewRequest request = new ConfirmReviewRequest(deviceReq, null);

        mockMvc.perform(post("/api/v1/review/tasks/" + testTask.getId() + "/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Task already processed"));
    }
    
    @Test
    void confirmTask_withBlankDeviceName_shouldReturn400() throws Exception {
        CreateDeviceRequest deviceReq = new CreateDeviceRequest("", "Brand", "Model", "LAPTOP", LocalDate.now());
        ConfirmReviewRequest request = new ConfirmReviewRequest(deviceReq, null);

        mockMvc.perform(post("/api/v1/review/tasks/" + testTask.getId() + "/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KEEPR-400"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'device.name')].message")
                        .value(org.hamcrest.Matchers.hasItem("Product name is required")));
    }
    
    @Test
    void getTask_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/review/tasks/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String obtainJwt(String phoneNumber) throws Exception {
        mockMvc.perform(post("/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"phoneNumber\": \"%s\"}", phoneNumber)))
                .andExpect(status().isOk());

        String code = requireNonNull(jdbcTemplate.queryForObject(
                "SELECT otp_code FROM auth_otp WHERE phone_number = ? ORDER BY expires_at DESC LIMIT 1",
                String.class, phoneNumber));

        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"phoneNumber\": \"%s\", \"otpCode\": \"%s\"}", phoneNumber, code)))
                .andExpect(status().isOk())
                .andReturn();

        var jsonNode = objectMapper.readTree(verifyResult.getResponse().getContentAsString()).get("accessToken");
        assertThat(jsonNode)
                .withFailMessage("Expected 'accessToken' in the verify-otp response")
                .isNotNull();
        return jsonNode.asText();
    }
}
