package com.keepr.auth.service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for JWT token generation and validation.
 * Uses HMAC SHA256 signing with JJWT 0.12.6.
 */
@Service
@Slf4j
public class JwtService {

    private static final int MINIMUM_SECRET_LENGTH = 32;
    private static final long TOKEN_EXPIRY_MINUTES = 15;
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final SecretKey signingKey;

    /**
     * Constructs the JwtService and validates the secret key length.
     *
     * @param secret the JWT signing secret from configuration
     */
    public JwtService(@Value("${keepr.jwt.secret}") String secret) {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MINIMUM_SECRET_LENGTH + " characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generates a JWT access token containing user identity claims.
     *
     * @param userId      the user's UUID
     * @param householdId the user's household UUID
     * @param phoneNumber the user's normalised phone number
     * @return the signed JWT string
     */
    public String generateToken(UUID userId, UUID householdId, String phoneNumber) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(TOKEN_EXPIRY_MINUTES * 60);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("householdId", householdId.toString())
                .claim("phoneNumber", phoneNumber)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT token, returning the claims if valid.
     *
     * @param token the JWT string
     * @return the parsed claims, or null if the token is invalid
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: {}", ex.getMessage());
            return null;
        } catch (JwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return null;
        }
    }
}
