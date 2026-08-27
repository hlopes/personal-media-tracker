package org.hlopes.library.dto;

import java.time.Instant;
import java.util.UUID;

import org.hlopes.catalog.dto.MediaItemDto;

public record LibraryEntryResponse(UUID id, String status, MediaItemDto mediaItem, Instant createdAt, Integer rating) {}
