package com.keepr.auth.controller;

import java.util.Map;

import com.keepr.auth.dto.AuthResponse;
import com.keepr.auth.dto.SendOtpRequest;
import com.keepr.auth.dto.VerifyOtpRequest;
import com.keepr.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * Handles OTP-based login and JWT token issuance.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Sends a 6-digit OTP to the provided phone number.
     *
     * @param request the send-otp request containing the phone number
     * @return 200 OK with a success message
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    /**
     * Verifies the OTP and returns a JWT access token.
     *
     * @param request the verify-otp request containing phone number and OTP code
     * @return 200 OK with the authentication response
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }
}
