package org.hlopes.library.dto;

import java.time.Instant;
import java.util.UUID;

public record SeasonWatchResponse(UUID id, UUID seasonId, Integer rating, Instant watchedAt) {}
