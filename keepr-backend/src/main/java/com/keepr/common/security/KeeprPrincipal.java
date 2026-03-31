package com.keepr.common.security;

import java.util.UUID;

/**
 * Stateless principal record carrying JWT claims through the security context.
 * Does NOT implement UserDetails — JWT is fully stateless.
 *
 * @param userId      the authenticated user's UUID
 * @param householdId the user's household UUID
 * @param phoneNumber the user's normalised phone number
 */
public record KeeprPrincipal(UUID userId, UUID householdId, String phoneNumber) {
}
