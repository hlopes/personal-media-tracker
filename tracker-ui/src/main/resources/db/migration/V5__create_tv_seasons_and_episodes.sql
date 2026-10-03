-- V5: tv_seasons and tv_episodes cached from Catalog (TMDB) for TV Series granular tracking
CREATE TABLE tv_seasons (
    id UUID PRIMARY KEY,
    media_item_id UUID NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    season_number INT NOT NULL CHECK (season_number >= 0),
    name VARCHAR(500),
    episode_count INT,
    poster_path VARCHAR(500),
    air_date DATE,
    overview TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_tv_seasons_media_season ON tv_seasons (media_item_id, season_number);
CREATE INDEX ix_tv_seasons_media ON tv_seasons (media_item_id);
CREATE TRIGGER update_tv_seasons_updated_at BEFORE UPDATE ON tv_seasons FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE tv_episodes (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES tv_seasons(id) ON DELETE CASCADE,
    media_item_id UUID NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    season_number INT NOT NULL CHECK (season_number >= 0),
    episode_number INT NOT NULL CHECK (episode_number >= 1),
    title VARCHAR(500) NOT NULL,
    synopsis TEXT,
    still_path VARCHAR(500),
    air_date DATE,
    runtime INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_tv_episodes_season_episode ON tv_episodes (season_id, episode_number);
CREATE UNIQUE INDEX uk_tv_episodes_media_season_episode ON tv_episodes (media_item_id, season_number, episode_number);
CREATE INDEX ix_tv_episodes_media ON tv_episodes (media_item_id);
CREATE INDEX ix_tv_episodes_season ON tv_episodes (season_id);
CREATE TRIGGER update_tv_episodes_updated_at BEFORE UPDATE ON tv_episodes FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
