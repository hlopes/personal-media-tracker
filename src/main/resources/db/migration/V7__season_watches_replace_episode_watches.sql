-- V7: Replace episode_watches with season_watches for simplified TV tracking (season-level atomic watch)
-- Drop episode_watches (hard cut, no migration of partial episode data as per ADR 0006)
DROP TABLE IF EXISTS episode_watches CASCADE;

CREATE TABLE season_watches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES tv_seasons(id) ON DELETE CASCADE,
    rating SMALLINT CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    watched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, season_id)
);

CREATE INDEX ix_season_watches_user ON season_watches (user_id);
CREATE INDEX ix_season_watches_season ON season_watches (season_id);

CREATE TRIGGER update_season_watches_updated_at BEFORE UPDATE ON season_watches FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
