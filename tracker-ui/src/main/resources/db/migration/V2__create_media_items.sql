-- V2: media_items cached from Catalog (TMDB) — lazy-cached snapshot
CREATE TABLE media_items (
    id UUID PRIMARY KEY,
    external_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('MOVIE', 'TV_SERIES')),
    title VARCHAR(500) NOT NULL,
    synopsis TEXT,
    poster_path VARCHAR(500),
    backdrop_path VARCHAR(500),
    release_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_media_items_external ON media_items (external_id, media_type);

CREATE TRIGGER update_media_items_updated_at BEFORE UPDATE ON media_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
