package com.keepr.auth.dto;

/**
 * Request body for the verify-otp endpoint.
 *
 * @param phoneNumber the user's phone number
 * @param otpCode     the 6-digit OTP code to verify
 */
public record VerifyOtpRequest(String phoneNumber, String otpCode) {
}
