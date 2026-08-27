package org.hlopes.auth.entity;

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
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
            @UniqueConstraint(name = "uk_users_verification_token", columnNames = "verification_token")
        })
@Getter
@Setter
@NoArgsConstructor
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "email", nullable = false, length = 255)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    public String passwordHash;

    @Column(name = "verified", nullable = false)
    public boolean verified = false;

    @Column(name = "verification_token", length = 255)
    public String verificationToken;

    @Column(name = "verification_token_expiry")
    public Instant verificationTokenExpiry;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
