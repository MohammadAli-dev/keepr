package com.keepr.auth.dto;

/**
 * Response body returned after successful OTP verification.
 *
 * @param accessToken the JWT access token
 * @param isNewUser   whether the user was newly created during this authentication
 */
public record AuthResponse(String accessToken, boolean isNewUser) {
}
