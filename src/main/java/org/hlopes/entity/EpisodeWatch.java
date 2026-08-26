package org.hlopes.entity;

import java.time.Instant;
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
        name = "episode_watches",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_episode_watches_user_episode",
                    columnNames = {"user_id", "episode_id"})
        },
        indexes = {
            @Index(name = "ix_episode_watches_user", columnList = "user_id"),
            @Index(name = "ix_episode_watches_episode", columnList = "episode_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class EpisodeWatch extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    public TvEpisode episode;

    @Column(name = "rating")
    public Integer rating;

    @Column(name = "watched_at", nullable = false)
    public Instant watchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
