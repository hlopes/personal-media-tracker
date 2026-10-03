package org.hlopes.library.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hlopes.library.entity.SeasonWatch;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SeasonWatchRepository implements PanacheRepositoryBase<SeasonWatch, UUID> {

    public Optional<SeasonWatch> findByUserIdAndSeasonId(UUID userId, UUID seasonId) {
        return find("user.id = ?1 and season.id = ?2", userId, seasonId).firstResultOptional();
    }

    public List<SeasonWatch> findByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return find("user.id = ?1 and season.mediaItem.id = ?2", userId, mediaItemId)
                .list();
    }

    public long countByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return count("user.id = ?1 and season.mediaItem.id = ?2 and season.seasonNumber != 0", userId, mediaItemId);
    }

    public long countWatchedByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return count("user.id = ?1 and season.mediaItem.id = ?2 and season.seasonNumber != 0", userId, mediaItemId);
    }

    public void deleteByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        delete("user.id = ?1 and season.mediaItem.id = ?2", userId, mediaItemId);
    }
}
