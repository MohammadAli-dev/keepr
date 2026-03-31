package com.keepr.auth.dto;

/**
 * Request body for the send-otp endpoint.
 *
 * @param phoneNumber the user's phone number
 */
public record SendOtpRequest(String phoneNumber) {
}
