-- V6: Episode Watches for TV Series granular tracking + relax COMPLETED rating constraint for TV
-- Drop the global COMPLETED => rating IS NOT NULL check; for TV Series with granular watches series rating becomes optional
ALTER TABLE library_entries DROP CONSTRAINT IF EXISTS chk_library_entries_rating_completed;

CREATE TABLE episode_watches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    episode_id UUID NOT NULL REFERENCES tv_episodes(id) ON DELETE CASCADE,
    rating SMALLINT CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    watched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, episode_id)
);

CREATE INDEX ix_episode_watches_user ON episode_watches (user_id);
CREATE INDEX ix_episode_watches_episode ON episode_watches (episode_id);

CREATE TRIGGER update_episode_watches_updated_at BEFORE UPDATE ON episode_watches FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
