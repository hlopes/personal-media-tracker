package org.hlopes.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required") @Email(message = "email must be valid") @Size(max = 255) String email,
        @NotBlank(message = "password is required")
                @Size(min = 8, max = 255, message = "password must be at least 8 characters")
                String password) {}
