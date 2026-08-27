package org.hlopes.library.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hlopes.library.entity.LibraryEntry;
import org.hlopes.library.entity.StatusEnum;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LibraryEntryRepository implements PanacheRepositoryBase<LibraryEntry, UUID> {

    public Optional<LibraryEntry> findByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return find("user.id = ?1 and mediaItem.id = ?2", userId, mediaItemId).firstResultOptional();
    }

    public Optional<LibraryEntry> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public List<LibraryEntry> findByUserIdAndStatus(UUID userId, StatusEnum status, int page, int size) {
        return find("user.id = ?1 and status = ?2", Sort.descending("createdAt"), userId, status)
                .page(Page.of(page, size))
                .list();
    }

    public long countByUserIdAndStatus(UUID userId, StatusEnum status) {
        return count("user.id = ?1 and status = ?2", userId, status);
    }

    public boolean existsByUserIdAndMediaItemId(UUID userId, UUID mediaItemId) {
        return count("user.id = ?1 and mediaItem.id = ?2", userId, mediaItemId) > 0;
    }
}
