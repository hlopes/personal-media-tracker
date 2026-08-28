# TV Seasons/Episodes as lazy-cached Catalog snapshot with derived Library Entry Status — superseded by ADR 0006

**Status: superseded by ADR 0006 — season-level watch replaces episode-level watch.**

# TV Seasons/Episodes as lazy-cached Catalog snapshot with derived Library Entry Status

TV Series tracking cannot be one-shot like Movie; it needs Season/Episode granularity. We chose to cache tv_seasons and tv_episodes locally per MediaItem (unique media_item_id+season_number and season_id+episode_number) populated on first TV detail via TMDB tv/{id} + tv/{id}/season/{n} fan-out with 24h staleness, rather than live-fetching each render. Episode watches are stored as User x Episode rows (episode_watches) with nullable Rating 1-5; Season completeness and Library Entry Status (WISHLIST/IN_PROGRESS/COMPLETED) are derived from episode counts (Specials season 0 excluded). This mirrors ADR 0004 lazy-cache (fast detail, survive TMDB downtime via cached fallback, share cache across Users) and keeps a single primary HTTP+Qute seam; alternatives of storing only season-level state or fetching live without cache were rejected for slow page loads, no progress queries, and no FK integrity.
