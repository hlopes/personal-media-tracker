package org.hlopes.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tv_seasons",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_tv_seasons_media_season",
                    columnNames = {"media_item_id", "season_number"})
        },
        indexes = {@Index(name = "ix_tv_seasons_media", columnList = "media_item_id")})
@Getter
@Setter
@NoArgsConstructor
public class TvSeason extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    public MediaItem mediaItem;

    @Column(name = "season_number", nullable = false)
    public Integer seasonNumber;

    @Column(name = "name", length = 500)
    public String name;

    @Column(name = "episode_count")
    public Integer episodeCount;

    @Column(name = "poster_path", length = 500)
    public String posterPath;

    @Column(name = "air_date")
    public LocalDate airDate;

    @Column(name = "overview", columnDefinition = "TEXT")
    public String overview;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
