package org.hlopes.catalog.dto;

import java.util.List;
import java.util.UUID;

public record MediaDetailEnrichedResponse(
        MediaItemDto mediaItem,
        List<CastDto> cast,
        DirectorDto director,
        String posterUrl,
        String backdropUrl,
        String imageBase,
        boolean alreadyInWatchlist,
        boolean alreadyInWatched,
        String currentStatus,
        Short currentRating,
        UUID currentEntryId,
        List<EnrichedSeasonDto> seasons) {}
