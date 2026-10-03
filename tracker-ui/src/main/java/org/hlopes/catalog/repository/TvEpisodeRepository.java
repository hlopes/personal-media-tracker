package org.hlopes.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.hlopes.catalog.entity.TvEpisode;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TvEpisodeRepository implements PanacheRepositoryBase<TvEpisode, UUID> {

    public List<TvEpisode> findBySeasonId(UUID seasonId) {
        return find("season.id = ?1", Sort.ascending("episodeNumber"), seasonId).list();
    }

    public List<TvEpisode> findByMediaItemId(UUID mediaItemId) {
        return find("mediaItem.id = ?1", Sort.ascending("seasonNumber", "episodeNumber"), mediaItemId)
                .list();
    }

    public List<TvEpisode> findByMediaItemIdAndSeasonNumber(UUID mediaItemId, int seasonNumber) {
        return find(
                        "mediaItem.id = ?1 and seasonNumber = ?2",
                        Sort.ascending("episodeNumber"),
                        mediaItemId,
                        seasonNumber)
                .list();
    }
}
