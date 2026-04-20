package com.minibank.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.minibank.user.entity.User;

/**
 * User Repository for database operations.
 *
 * Extends JpaRepository for basic CRUD operations.
 * Custom queries for user lookup by various criteria.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email address.
     *
     * @param email the email address to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by phone number.
     *
     * @param phone the phone number to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByPhone(String phone);

    /**
     * Finds a user by national ID.
     *
     * @param nationalId the national ID to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByNationalId(String nationalId);

    /**
     * Checks if a user exists with the given email.
     *
     * @param email the email to check
     * @return true if a user exists with this email
     */
    boolean existsByEmail(String email);

    /**
     * Checks if a user exists with the given phone.
     *
     * @param phone the phone to check
     * @return true if a user exists with this phone
     */
    boolean existsByPhone(String phone);

    /**
     * Finds users whose accounts are locked until a time before now.
     * Used for unlocking accounts after lockout period.
     *
     * @param now the current time
     * @return list of users that should be unlocked
     */
    @Query("SELECT u FROM User u WHERE u.status = 'LOCKED' AND u.lockedUntil < :now")
    java.util.List<User> findLockedAccountsToUnlock(@Param("now") LocalDateTime now);

    /**
     * Finds active users who haven't logged in for a given period.
     * Used for security review and dormant account management.
     *
     * @param threshold the date threshold
     * @return list of dormant users
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND u.lastLoginAt < :threshold")
    java.util.List<User> findDormantAccounts(@Param("threshold") LocalDateTime threshold);

    /**
     * Counts users by status.
     *
     * @param status the status to count
     * @return number of users with the given status
     */
    long countByStatus(User.UserStatus status);

    /**
     * Finds a user by ID and checks if they are active.
     *
     * @param id the user ID
     * @return Optional containing the active user if found
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.status = 'ACTIVE'")
    Optional<User> findActiveById(@Param("id") UUID id);

    /**
     * Finds a user by email ignoring case.
     *
     * @param email the email to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByEmailIgnoreCase(String email);
}
