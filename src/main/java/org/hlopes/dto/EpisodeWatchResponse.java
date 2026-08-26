package org.hlopes.dto;

import java.time.Instant;
import java.util.UUID;

public record EpisodeWatchResponse(UUID id, UUID episodeId, Integer rating, Instant watchedAt) {}
