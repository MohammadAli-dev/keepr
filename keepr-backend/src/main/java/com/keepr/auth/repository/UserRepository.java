package com.keepr.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.keepr.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link User} entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their normalised phone number.
     *
     * @param phoneNumber the normalised phone number
     * @return the user if found
     */
    Optional<User> findByPhoneNumber(String phoneNumber);
}
