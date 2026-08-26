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
        name = "tv_episodes",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_tv_episodes_season_episode",
                    columnNames = {"season_id", "episode_number"}),
            @UniqueConstraint(
                    name = "uk_tv_episodes_media_season_episode",
                    columnNames = {"media_item_id", "season_number", "episode_number"})
        },
        indexes = {
            @Index(name = "ix_tv_episodes_media", columnList = "media_item_id"),
            @Index(name = "ix_tv_episodes_season", columnList = "season_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class TvEpisode extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    public TvSeason season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    public MediaItem mediaItem;

    @Column(name = "season_number", nullable = false)
    public Integer seasonNumber;

    @Column(name = "episode_number", nullable = false)
    public Integer episodeNumber;

    @Column(name = "title", nullable = false, length = 500)
    public String title;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    public String synopsis;

    @Column(name = "still_path", length = 500)
    public String stillPath;

    @Column(name = "air_date")
    public LocalDate airDate;

    @Column(name = "runtime")
    public Integer runtime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
