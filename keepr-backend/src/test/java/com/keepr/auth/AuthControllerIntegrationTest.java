package com.keepr.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepr.AbstractIntegrationTest;
import com.keepr.auth.dto.SendOtpRequest;
import com.keepr.auth.dto.VerifyOtpRequest;
import com.keepr.auth.model.AuthOtp;
import com.keepr.auth.repository.AuthOtpRepository;
import com.keepr.auth.repository.HouseholdRepository;
import com.keepr.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Sprint 2 authentication flow.
 * Uses shared Testcontainers from AbstractIntegrationTest.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthOtpRepository authOtpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @BeforeEach
    void cleanDb() {
        authOtpRepository.deleteAll();
    }

    // -------------------------------------------------------
    // Send OTP Tests
    // -------------------------------------------------------

    @Test
    void sendOtp_validPhone_returns200() throws Exception {
        SendOtpRequest request = new SendOtpRequest("9876543210");

        mockMvc.perform(post("/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent successfully"));
    }

    @Test
    void sendOtp_invalidPhone_returns400() throws Exception {
        SendOtpRequest request = new SendOtpRequest("123");

        mockMvc.perform(post("/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendOtp_phoneNormalisationWorks() throws Exception {
        SendOtpRequest request = new SendOtpRequest("+91-9876543210");

        mockMvc.perform(post("/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify OTP stored with normalised phone
        List<AuthOtp> otps = authOtpRepository.findAll();
        assertThat(otps).hasSize(1);
        assertThat(otps.get(0).getPhoneNumber()).isEqualTo("9876543210");
    }

    // -------------------------------------------------------
    // Verify OTP Tests
    // -------------------------------------------------------

    @Test
    void verifyOtp_correctOtp_returnsAccessToken() throws Exception {
        String phone = "9876543211";
        String otpCode = seedOtp(phone, 10);

        VerifyOtpRequest request = new VerifyOtpRequest(phone, otpCode);

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    @Test
    void verifyOtp_newUser_createsUserHouseholdAndMember() throws Exception {
        String phone = "9876543212";
        String otpCode = seedOtp(phone, 10);

        VerifyOtpRequest request = new VerifyOtpRequest(phone, otpCode);

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify user created
        assertThat(userRepository.findByPhoneNumber(phone)).isPresent();

        // Verify household created
        var user = userRepository.findByPhoneNumber(phone).get();
        assertThat(householdRepository.findByOwnerUserId(user.getId())).isPresent();
    }

    @Test
    void verifyOtp_existingUser_returnsIsNewUserFalse() throws Exception {
        String phone = "9876543213";

        // First login — create the user
        String otp1 = seedOtp(phone, 10);
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(phone, otp1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));

        // Second login — same user
        String otp2 = seedOtp(phone, 10);
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(phone, otp2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false));
    }

    @Test
    void verifyOtp_wrongOtp_returns401() throws Exception {
        String phone = "9876543214";
        seedOtp(phone, 10);

        VerifyOtpRequest request = new VerifyOtpRequest(phone, "000000");

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtp_expiredOtp_returns401() throws Exception {
        String phone = "9876543215";
        seedOtp(phone, -1); // expired 1 minute ago

        VerifyOtpRequest request = new VerifyOtpRequest(phone, "123456");

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtp_otpConsumedAfterSuccess() throws Exception {
        String phone = "9876543216";
        String otpCode = seedOtp(phone, 10);

        // First attempt — should succeed
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(phone, otpCode))))
                .andExpect(status().isOk());

        // Second attempt with same OTP — should fail (consumed)
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(phone, otpCode))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtp_otpStoredInDatabase() throws Exception {
        String phone = "9876543217";
        SendOtpRequest request = new SendOtpRequest(phone);

        mockMvc.perform(post("/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify OTP exists in database
        List<AuthOtp> otps = authOtpRepository.findAll();
        assertThat(otps).isNotEmpty();
        assertThat(otps.stream().anyMatch(o -> o.getPhoneNumber().equals(phone))).isTrue();
    }

    @Test
    void verifyOtp_concurrentRequests_onlyOneUserCreated() throws Exception {
        String phone = "9876543218";
        String otpCode = "654321";

        // Seed multiple OTPs under the same phone so concurrent threads can each find one
        for (int i = 0; i < 5; i++) {
            AuthOtp otp = new AuthOtp();
            otp.setPhoneNumber(phone);
            otp.setOtpCode(otpCode);
            otp.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
            authOtpRepository.save(otp);
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/auth/verify-otp")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            new VerifyOtpRequest(phone, otpCode))))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // Expected for losers of the race
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Only one user should exist regardless of how many threads succeeded
        long userCount = userRepository.findAll().stream()
                .filter(u -> u.getPhoneNumber().equals(phone))
                .count();
        assertThat(userCount).isEqualTo(1);
    }

    // -------------------------------------------------------
    // Security Tests
    // -------------------------------------------------------

    @Test
    void healthEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpoint_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validJwt_returnsNotUnauthorized() throws Exception {
        // Create a user first
        String phone = "9876543219";
        String otpCode = seedOtp(phone, 10);
        MvcResult authResult = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(phone, otpCode))))
                .andReturn();

        String token = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Use the token — status should NOT be 401 (proving the JWT was accepted)
        int status = mockMvc.perform(get("/api/protected")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }

    @Test
    void protectedEndpoint_expiredJwt_returns401() throws Exception {
        // Use a clearly invalid/tampered token to test rejection
        String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.invalid";

        mockMvc.perform(get("/api/protected")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Seeds an OTP directly into the database for testing.
     *
     * @param phone        the phone number
     * @param expiryMinutes minutes from now until expiry (negative = expired)
     * @return the OTP code
     */
    private String seedOtp(String phone, int expiryMinutes) {
        String otpCode = "123456";
        AuthOtp otp = new AuthOtp();
        otp.setPhoneNumber(phone);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES));
        authOtpRepository.save(otp);
        return otpCode;
    }
}
