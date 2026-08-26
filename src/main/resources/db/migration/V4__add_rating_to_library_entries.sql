-- V4: add Rating to Library Entry — for Watched/COMPLETED view (phase 3)
ALTER TABLE library_entries ADD COLUMN rating SMALLINT CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5));

-- Rating must be present when COMPLETED, absent when WISHLIST; other statuses allow nullable rating
ALTER TABLE library_entries ADD CONSTRAINT chk_library_entries_rating_completed CHECK (status <> 'COMPLETED' OR rating IS NOT NULL);
ALTER TABLE library_entries ADD CONSTRAINT chk_library_entries_rating_wishlist CHECK (status <> 'WISHLIST' OR rating IS NULL);

CREATE INDEX ix_library_entries_user_status_rating ON library_entries (user_id, status, rating);
