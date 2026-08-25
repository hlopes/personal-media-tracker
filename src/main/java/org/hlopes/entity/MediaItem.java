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
        name = "media_items",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_media_items_external",
                    columnNames = {"external_id", "media_type"})
        })
@Getter
@Setter
@NoArgsConstructor
public class MediaItem extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "external_id", nullable = false)
    public Long externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    public MediaType mediaType;

    @Column(name = "title", nullable = false, length = 500)
    public String title;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    public String synopsis;

    @Column(name = "poster_path", length = 500)
    public String posterPath;

    @Column(name = "backdrop_path", length = 500)
    public String backdropPath;

    @Column(name = "release_date")
    public LocalDate releaseDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
