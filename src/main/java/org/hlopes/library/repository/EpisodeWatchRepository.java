package org.hlopes.library.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hlopes.library.entity.EpisodeWatch;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EpisodeWatchRepository implements PanacheRepositoryBase<EpisodeWatch, UUID> {

    public Optional<EpisodeWatch> findByUserIdAndEpisodeId(UUID userId, UUID episodeId) {
        return find("user.id = ?1 and episode.id = ?2", userId, episodeId).firstResultOptional();
    }

    public List<EpisodeWatch> findByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return find("user.id = ?1 and episode.mediaItem.id = ?2", userId, mediaItemId)
                .list();
    }

    public List<EpisodeWatch> findByUserIdAndSeasonId(UUID userId, UUID seasonId) {
        return find("user.id = ?1 and episode.season.id = ?2", userId, seasonId).list();
    }

    public long countByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return count("user.id = ?1 and episode.mediaItem.id = ?2 and episode.seasonNumber != 0", userId, mediaItemId);
    }

    public long countByUserIdAndSeasonId(UUID userId, UUID seasonId) {
        return count("user.id = ?1 and episode.season.id = ?2", userId, seasonId);
    }

    public void deleteByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        delete("user.id = ?1 and episode.mediaItem.id = ?2", userId, mediaItemId);
    }
}
