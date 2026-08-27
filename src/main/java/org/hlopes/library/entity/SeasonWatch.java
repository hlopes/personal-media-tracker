package org.hlopes.library.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hlopes.auth.entity.User;
import org.hlopes.catalog.entity.TvSeason;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "season_watches",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_season_watches_user_season",
                    columnNames = {"user_id", "season_id"})
        },
        indexes = {
            @Index(name = "ix_season_watches_user", columnList = "user_id"),
            @Index(name = "ix_season_watches_season", columnList = "season_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class SeasonWatch extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    public TvSeason season;

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
