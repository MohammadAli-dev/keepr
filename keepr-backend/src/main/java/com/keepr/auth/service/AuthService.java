package com.keepr.auth.service;

import java.time.Instant;

import com.keepr.auth.dto.AuthResponse;
import com.keepr.auth.dto.SendOtpRequest;
import com.keepr.auth.dto.VerifyOtpRequest;
import com.keepr.auth.model.Household;
import com.keepr.auth.model.User;
import com.keepr.auth.repository.HouseholdRepository;
import com.keepr.auth.repository.UserRepository;
import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration service for authentication flows.
 * Handles phone normalisation, OTP orchestration, user/household provisioning, and JWT issuance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String PHONE_REGEX = "^[0-9]{10}$";
    private static final String OTP_REGEX = "^[0-9]{6}$";

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final EntityManager entityManager;

    /**
     * Normalises the phone number and triggers OTP generation.
     *
     * @param request the send-otp request
     */
    public void sendOtp(SendOtpRequest request) {
        String phone = normalisePhone(request.phoneNumber());
        otpService.generateAndStoreOtp(phone);
    }

    /**
     * Verifies the OTP, provisions user/household if needed, and issues a JWT.
     * Wrapped in {@code @Transactional} to hold the pessimistic lock during OTP verification.
     *
     * @param request the verify-otp request
     * @return the authentication response containing the JWT
     */
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String phone = normalisePhone(request.phoneNumber());
        validateOtpFormat(request.otpCode());

        // Step 1: Validate and immediately consume (delete) the OTP
        boolean valid = otpService.validateAndConsumeOtp(phone, request.otpCode());
        if (!valid) {
            throw new KeeprException(ErrorCode.UNAUTHORIZED, "Invalid or expired OTP");
        }

        // Step 2: Provision or fetch user (only runs if OTP was already deleted)
        User existingUser = userRepository.findByPhoneNumber(phone).orElse(null);
        boolean isNewUser = (existingUser == null);
        User user = isNewUser ? createUserWithHousehold(phone) : existingUser;

        // Step 3: Fetch household and issue JWT
        Household household = householdRepository.findByOwnerUserId(user.getId())
                .orElseThrow(() -> new KeeprException(
                        ErrorCode.INTERNAL_ERROR,
                        "Household not found for user " + user.getId()));

        String token = jwtService.generateToken(user.getId(), household.getId(), phone);
        return new AuthResponse(token, isNewUser);
    }

    /**
     * Creates a new user, household, and household_members entry atomically.
     * Handles concurrent creation race conditions via unique constraint retry.
     *
     * @param phone the normalised phone number
     * @return the newly created (or existing) user
     */
    private User createUserWithHousehold(String phone) {
        try {
            User user = new User();
            user.setPhoneNumber(phone);
            user.setName("Keepr User");
            user = userRepository.save(user);

            Household household = new Household();
            household.setName(user.getName() + "'s Home");
            household.setOwnerUserId(user.getId());
            household = householdRepository.save(household);

            // Native insert for household_members (no entity needed)
            entityManager.createNativeQuery(
                            "INSERT INTO household_members (household_id, user_id, role, joined_at) "
                                    + "VALUES (:householdId, :userId, 'OWNER', :joinedAt)")
                    .setParameter("householdId", household.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("joinedAt", Instant.now())
                    .executeUpdate();

            log.info("Created new user and household for phone {}", OtpService.maskPhone(phone));
            return user;

        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread created the user first
            log.warn("Race condition on user creation for phone {}, fetching existing",
                    OtpService.maskPhone(phone));
            return userRepository.findByPhoneNumber(phone)
                    .orElseThrow(() -> new KeeprException(
                            ErrorCode.INTERNAL_ERROR,
                            "Failed to create or find user for phone " + OtpService.maskPhone(phone)));
        }
    }

    /**
     * Normalises a phone number to 10-digit format by stripping country code and non-digits.
     *
     * @param rawPhone the raw phone input
     * @return the normalised 10-digit phone number
     * @throws KeeprException if the phone number is invalid after normalisation
     */
    static String normalisePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Phone number is required");
        }
        // Strip all non-digit characters
        String digits = rawPhone.replaceAll("[^0-9]", "");
        // Strip leading country code (91 for India)
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches(PHONE_REGEX)) {
            throw new KeeprException(ErrorCode.BAD_REQUEST,
                    "Invalid phone number. Must be 10 digits after normalisation.");
        }
        return digits;
    }

    /**
     * Validates that the OTP code is exactly 6 numeric digits.
     *
     * @param otpCode the OTP code to validate
     * @throws KeeprException if the format is invalid
     */
    private void validateOtpFormat(String otpCode) {
        if (otpCode == null || !otpCode.matches(OTP_REGEX)) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "OTP must be exactly 6 numeric digits");
        }
    }
}
