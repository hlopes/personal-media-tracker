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
        boolean alreadyInWishlist,
        boolean alreadyInWatched,
        String currentStatus,
        Integer currentRating,
        UUID currentEntryId,
        List<EnrichedSeasonDto> seasons) {}
