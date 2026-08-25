-- V3: library_entries — association User <-> MediaItem for Wishlist (phase 2: WISHLIST only)
CREATE TABLE library_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_item_id UUID NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('WISHLIST', 'IN_PROGRESS', 'COMPLETED', 'DROPPED', 'ON_HOLD')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_library_entries_user_media ON library_entries (user_id, media_item_id);
CREATE INDEX ix_library_entries_user_status ON library_entries (user_id, status);

CREATE TRIGGER update_library_entries_updated_at BEFORE UPDATE ON library_entries FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
