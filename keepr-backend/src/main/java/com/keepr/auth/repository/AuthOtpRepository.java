package com.keepr.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.keepr.auth.model.AuthOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AuthOtp} entities.
 * Uses pessimistic locking to prevent race conditions during OTP verification.
 */
@Repository
public interface AuthOtpRepository extends JpaRepository<AuthOtp, UUID> {

    /**
     * Fetches the most recent OTP for a phone number with a pessimistic write lock.
     * This prevents concurrent verification attempts from consuming the same OTP.
     *
     * @param phoneNumber the normalised phone number
     * @return the latest OTP record if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM AuthOtp o WHERE o.phoneNumber = :phoneNumber ORDER BY o.createdAt DESC LIMIT 1")
    Optional<AuthOtp> findLatestByPhoneForUpdate(@Param("phoneNumber") String phoneNumber);

    /**
     * Deletes all OTP records for a given phone number.
     * Called after successful verification to ensure single-use idempotency.
     *
     * @param phoneNumber the normalised phone number
     */
    void deleteAllByPhoneNumber(String phoneNumber);
}
