package com.minibank.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import com.minibank.user.entity.User;

/**
 * DTO for user response.
 *
 * Excludes sensitive information like password hash.
 * Used for API responses containing user data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String fullName;
    private String status;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    /**
     * Maps User entity to UserResponse DTO.
     *
     * @param user the user entity
     * @return the response DTO
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .status(user.getStatus().name())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
