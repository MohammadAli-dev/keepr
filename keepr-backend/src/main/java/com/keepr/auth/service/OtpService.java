package com.keepr.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;

import com.keepr.auth.model.AuthOtp;
import com.keepr.auth.repository.AuthOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for OTP generation, storage, and DB-based validation.
 * Contains NO business logic related to user creation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 10;

    private final AuthOtpRepository authOtpRepository;
    private final Random secureRandom = new Random();

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * Generates a 6-digit OTP, stores it in the database, and logs it for dev environments.
     *
     * @param normalizedPhone the already-normalised phone number
     * @return the generated OTP code
     */
    public String generateAndStoreOtp(String normalizedPhone) {
        String otpCode = generateOtpCode();

        AuthOtp otp = new AuthOtp();
        otp.setPhoneNumber(normalizedPhone);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        authOtpRepository.save(otp);

        if ("local".equals(activeProfile) || "test".equals(activeProfile)) {
            log.info("OTP for {} is {}", maskPhone(normalizedPhone), otpCode);
        } else {
            log.info("OTP sent to {}", maskPhone(normalizedPhone));
        }

        return otpCode;
    }

    /**
     * Validates the OTP against the DB using pessimistic locking, then immediately deletes it.
     * This method MUST be called within a {@code @Transactional} context for the lock to work.
     *
     * @param normalizedPhone the already-normalised phone number
     * @param otpCode         the 6-digit OTP code from the user
     * @return true if validation succeeded and OTP was consumed
     */
    public boolean validateAndConsumeOtp(String normalizedPhone, String otpCode) {
        Optional<AuthOtp> latestOtp = authOtpRepository.findLatestByPhoneForUpdate(normalizedPhone);

        if (latestOtp.isEmpty()) {
            log.warn("No OTP found for phone {}", maskPhone(normalizedPhone));
            return false;
        }

        AuthOtp otp = latestOtp.get();

        if (Instant.now().isAfter(otp.getExpiresAt())) {
            log.warn("OTP expired for phone {}", maskPhone(normalizedPhone));
            authOtpRepository.deleteAllByPhoneNumber(normalizedPhone);
            return false;
        }

        if (!otp.getOtpCode().equals(otpCode)) {
            log.warn("OTP mismatch for phone {}", maskPhone(normalizedPhone));
            return false;
        }

        // Immediately delete ALL OTPs for this phone before proceeding
        authOtpRepository.deleteAllByPhoneNumber(normalizedPhone);
        log.info("OTP verified and consumed for phone {}", maskPhone(normalizedPhone));
        return true;
    }

    private String generateOtpCode() {
        int otp = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(otp);
    }

    /**
     * Masks phone number for safe logging. Example: {@code ******3210}.
     *
     * @param phone the phone number to mask
     * @return the masked phone number
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "******" + phone.substring(phone.length() - 4);
    }
}
