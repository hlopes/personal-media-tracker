package org.hlopes.repository;

import java.util.Optional;
import java.util.UUID;

import org.hlopes.entity.User;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    public Optional<User> findByEmail(String email) {
        return find("email", email.toLowerCase()).firstResultOptional();
    }

    public Optional<User> findByVerificationToken(String token) {
        return find("verificationToken", token).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("email", email.toLowerCase()) > 0;
    }
}
