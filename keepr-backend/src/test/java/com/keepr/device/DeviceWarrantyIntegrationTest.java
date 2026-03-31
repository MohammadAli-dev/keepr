package com.keepr.device;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.auth.model.AuthOtp;
import com.keepr.auth.repository.AuthOtpRepository;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.warranty.repository.WarrantyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Sprint 3: Device  and Warranty endpoints.
 * Uses Testcontainers with real Postgres and Redis.
 * All tests authenticate via the OTP flow first to obtain a valid JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DeviceWarrantyIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
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
    private AuthOtpRepository authOtpRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private WarrantyRepository warrantyRepository;

    /**
     * Injects Testcontainers connection properties into the Spring context.
     *
     * @param registry the dynamic property registry
     */
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
        warrantyRepository.deleteAll();
        deviceRepository.deleteAll();
        authOtpRepository.deleteAll();
    }

    // -------------------------------------------------------
    // Device Creation Tests
    // -------------------------------------------------------

    @Test
    void createDevice_valid_returns200() throws Exception {
        String token = authenticateUser("9876540001");

        String body = """
                {
                  "name": "LG AC",
                  "brand": "LG",
                  "model": "DualCool",
                  "category": "APPLIANCE",
                  "purchaseDate": "2024-06-01"
                }
                """;

        mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("LG AC"))
                .andExpect(jsonPath("$.brand").value("LG"))
                .andExpect(jsonPath("$.model").value("DualCool"))
                .andExpect(jsonPath("$.category").value("APPLIANCE"))
                .andExpect(jsonPath("$.purchaseDate").value("2024-06-01"));
    }

    @Test
    void createDevice_futurePurchaseDate_returns400() throws Exception {
        String token = authenticateUser("9876540002");
        LocalDate futureDate = LocalDate.now().plusDays(30);

        String body = String.format("""
                {
                  "name": "Future Device",
                  "brand": "TestBrand",
                  "model": "TestModel",
                  "category": "ELECTRONICS",
                  "purchaseDate": "%s"
                }
                """, futureDate);

        mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------
    // Device Listing Tests
    // -------------------------------------------------------

    @Test
    void listDevices_returnsOnlyHouseholdDevices() throws Exception {
        String tokenA = authenticateUser("9876540003");
        String tokenB = authenticateUser("9876540004");

        // User A creates a device
        createTestDevice(tokenA, "User A Device");

        // User B creates a device
        createTestDevice(tokenB, "User B Device");

        // User A should only see their device
        MvcResult result = mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode devices = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(devices.size()).isEqualTo(1);
        assertThat(devices.get(0).get("name").asText()).isEqualTo("User A Device");
    }

    @Test
    void listDevices_emptyState_returnsEmptyList() throws Exception {
        String token = authenticateUser("9876540005");

        MvcResult result = mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode devices = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(devices.size()).isEqualTo(0);
    }

    @Test
    void listDevices_returnsOrderedByCreatedAtDesc() throws Exception {
        String token = authenticateUser("9876540006");

        // Create first device
        createTestDevice(token, "Older Device");

        // Small delay to ensure different createdAt timestamps
        Thread.sleep(50);

        // Create second device
        createTestDevice(token, "Newer Device");

        MvcResult result = mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode devices = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(devices.size()).isEqualTo(2);
        // Newest first
        assertThat(devices.get(0).get("name").asText()).isEqualTo("Newer Device");
        assertThat(devices.get(1).get("name").asText()).isEqualTo("Older Device");
    }

    // -------------------------------------------------------
    // Warranty Creation Tests
    // -------------------------------------------------------

    @Test
    void createWarranty_valid_returns200() throws Exception {
        String token = authenticateUser("9876540007");
        UUID deviceId = createTestDevice(token, "Warranty Test Device");

        String body = String.format("""
                {
                  "deviceId": "%s",
                  "type": "MANUFACTURER",
                  "startDate": "2024-06-01",
                  "endDate": "2025-06-01"
                }
                """, deviceId);

        mockMvc.perform(post("/warranties")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warrantyId").isNotEmpty())
                .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
                .andExpect(jsonPath("$.type").value("MANUFACTURER"))
                .andExpect(jsonPath("$.startDate").value("2024-06-01"))
                .andExpect(jsonPath("$.endDate").value("2025-06-01"));
    }

    @Test
    void createWarranty_invalidDate_returns400() throws Exception {
        String token = authenticateUser("9876540008");
        UUID deviceId = createTestDevice(token, "Date Test Device");

        // endDate before startDate
        String body = String.format("""
                {
                  "deviceId": "%s",
                  "type": "MANUFACTURER",
                  "startDate": "2025-06-01",
                  "endDate": "2024-06-01"
                }
                """, deviceId);

        mockMvc.perform(post("/warranties")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWarranty_deviceNotFound_returns404() throws Exception {
        String token = authenticateUser("9876540009");
        UUID fakeDeviceId = UUID.randomUUID();

        String body = String.format("""
                {
                  "deviceId": "%s",
                  "type": "MANUFACTURER",
                  "startDate": "2024-06-01",
                  "endDate": "2025-06-01"
                }
                """, fakeDeviceId);

        mockMvc.perform(post("/warranties")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWarranty_crossHouseholdDevice_returns404() throws Exception {
        String tokenA = authenticateUser("9876540010");
        String tokenB = authenticateUser("9876540011");

        // User A creates a device
        UUID deviceIdA = createTestDevice(tokenA, "User A's Device");

        // User B tries to attach a warranty to User A's device — should get 404
        String body = String.format("""
                {
                  "deviceId": "%s",
                  "type": "MANUFACTURER",
                  "startDate": "2024-06-01",
                  "endDate": "2025-06-01"
                }
                """, deviceIdA);

        mockMvc.perform(post("/warranties")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------
    // Security Tests
    // -------------------------------------------------------

    @Test
    void protectedEndpoints_requireAuth() throws Exception {
        // POST /devices without JWT
        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test\", \"category\": \"APPLIANCE\"}"))
                .andExpect(status().isUnauthorized());

        // GET /devices without JWT
        mockMvc.perform(get("/devices"))
                .andExpect(status().isUnauthorized());

        // POST /warranties without JWT
        mockMvc.perform(post("/warranties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\": \"" + UUID.randomUUID() + "\", \"type\": \"MANUFACTURER\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devicesScopedToHousehold_correctly() throws Exception {
        String tokenA = authenticateUser("9876540012");
        String tokenB = authenticateUser("9876540013");

        // Both users create devices
        createTestDevice(tokenA, "Device A1");
        createTestDevice(tokenA, "Device A2");
        createTestDevice(tokenB, "Device B1");

        // User A sees 2 devices
        MvcResult resultA = mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode devicesA = objectMapper.readTree(resultA.getResponse().getContentAsString());
        assertThat(devicesA.size()).isEqualTo(2);

        // User B sees 1 device
        MvcResult resultB = mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode devicesB = objectMapper.readTree(resultB.getResponse().getContentAsString());
        assertThat(devicesB.size()).isEqualTo(1);
        assertThat(devicesB.get(0).get("name").asText()).isEqualTo("Device B1");
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Authenticates a user via the OTP flow and returns the JWT access token.
     *
     * @param phone the phone number to authenticate
     * @return the JWT access token
     */
    private String authenticateUser(String phone) throws Exception {
        AuthOtp otp = new AuthOtp();
        otp.setPhoneNumber(phone);
        otp.setOtpCode("123456");
        otp.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        authOtpRepository.save(otp);

        MvcResult result = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.keepr.auth.dto.VerifyOtpRequest(phone, "123456"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /**
     * Creates a test device and returns its UUID.
     *
     * @param token      the JWT access token
     * @param deviceName the name for the device
     * @return the created device's UUID
     */
    private UUID createTestDevice(String token, String deviceName) throws Exception {
        String body = String.format("""
                {
                  "name": "%s",
                  "brand": "TestBrand",
                  "model": "TestModel",
                  "category": "APPLIANCE",
                  "purchaseDate": "2024-01-15"
                }
                """, deviceName);

        MvcResult result = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("deviceId").asText());
    }
}
