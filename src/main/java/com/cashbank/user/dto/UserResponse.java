package com.cashbank.user.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.cashbank.user.Role;
import com.cashbank.user.User;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        Role role,
        boolean enabled,
        Instant createdAt) implements Serializable {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhoneNumber(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
