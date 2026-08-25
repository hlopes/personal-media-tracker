package org.hlopes.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, boolean verified, Instant createdAt) {}
