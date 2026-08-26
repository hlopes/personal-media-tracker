package org.hlopes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hlopes.entity.TvSeason;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TvSeasonRepository implements PanacheRepositoryBase<TvSeason, UUID> {

    public List<TvSeason> findByMediaItemId(UUID mediaItemId) {
        return find("mediaItem.id = ?1", Sort.ascending("seasonNumber"), mediaItemId)
                .list();
    }

    public Optional<TvSeason> findByMediaItemIdAndSeasonNumber(UUID mediaItemId, int seasonNumber) {
        return find("mediaItem.id = ?1 and seasonNumber = ?2", mediaItemId, seasonNumber)
                .firstResultOptional();
    }
}
