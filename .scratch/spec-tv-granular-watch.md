# Spec: TV Series — Season/Episode Granular Watched with Rating

## Seams (for review — please confirm)

**Proposed highest seams (fewest, widest):**

1. **HTTP API + Qute pages — single primary seam covering all user-visible behavior:**
   - Existing Series-level: `POST /api/me/library {externalId, mediaType, status, rating}`, `PATCH /api/me/library/{id} {status, rating}`, `GET /api/me/library?status=WISHLIST|COMPLETED&page=&size=`, `DELETE /api/me/library/{id}` (unchanged for Movie; TV Series keeps this row but its `status`/`rating` becomes derived when granular data exists)
   - New Episode-granular (under the Series `Library Entry`): `GET /api/me/library/{id}/seasons` (seasons with progress), `GET /api/me/library/{id}/seasons/{seasonNumber}/episodes` (episodes with `watched` + `rating`), `POST /api/me/library/{id}/episodes/{episodeId}/watch {rating?}`, `PATCH .../watch {rating}`, `DELETE .../watch`, `POST /api/me/library/{id}/seasons/{seasonNumber}/watch {rating?}` (bulk episode creation)
   - Qute: `GET /media/tv/{externalId}` (TV detail with Season accordion → Episode rows, Mark Season/Episode as Watched + Rating), `GET /watched` (now shows progress `watched/total` and `progress%` for TV Series, stars remain per Library Entry or episode average), `GET /wishlist` and `GET /media/movie/{externalId}` (unchanged), nav `Wishlist | Watched`

   This reuses existing seams from `LibraryResource`/`PageResource`/`JwtCookieFilter` and is tested at the same layer as `WatchedLibraryTest` (QuarkusTest + RestAssured + HTML assertions). `CatalogService`/`TmdbClient` fan-out (`tv/{id}` + `tv/{id}/season/{n}`) is exercised only through the HTTP seam via the TV detail page; no mocked TMDB unit tests.

2. **Flyway migrations as second seam only** — persistence (`tv_seasons`, `tv_episodes`, `episode_watches`, relaxation of the `COMPLETED ⇒ rating NOT NULL` check for TV) is validated through the API/Qute seam above; no direct repository unit tests.

**No new low-level seams** (no service-layer stubs, no `EntityManager` mocks). If you prefer a second seam at `EpisodeWatchService` or `CatalogService`, call it out — otherwise we stay at one primary seam + migrations.

---

## Problem Statement

A `User` who tracks a `TV Series` (`MediaType` `TV_SERIES`) currently must mark the entire series as `Completed` (`Watched`) in one shot, with a single `Rating` on the `Library Entry`. This matches a `Movie` (one viewing session) but not the reality of a `TV Series`, which is consumed as `Season`s containing `Episode`s over many sessions. The `User` cannot record that they have watched Season 1 but not Season 2, or that they saw Episodes 1–3 of a season but not the rest — and they cannot assess individual episodes. They also cannot see progress (`3/10 episodes`) or resume where they left off. This forces either premature `Completed` or loss of history.

## Solution

Enrich the `Watched` feature for `TV Series` only (`Movie` stays one-shot) with `Season`/`Episode` granularity. On a `TV Series` detail page the `User` sees a list of `Season`s (collapsible accordion) and, when expanded, a list of `Episode`s for that `Season`. The `User` can mark a single `Episode` as watched (optionally with a `Rating` 1–5 visible after selection) or mark a whole `Season` as watched (bulk-marks all its `Episode`s). The `Library Entry` for the series derives its `Status` from episode progress: `WISHLIST` (0 watched) → `IN_PROGRESS` (some watched) → `COMPLETED` (all non-Specials episodes watched or explicit series mark). `Rating` for `TV Series` becomes optional at the series level when granular tracking is used; each `Episode Watch` may carry its own nullable `Rating`. The existing `Watched` filtered view remains series where `Status=COMPLETED`; elsewhere progress is surfaced. All `Catalog` data for seasons/episodes is lazy-cached locally from TMDB, mirroring the existing `MediaItem` cache.

## User Stories

1. As an authenticated `User`, I want to view a `TV Series` detail page as a list of `Season`s (ordered by `season_number`, with Specials `season_number=0` collapsed at bottom and excluded from progress), so that I see the series structure.
2. As an authenticated `User`, I want each `Season` row to show its name, episode count, poster, air date and progress `watched/total` (e.g., `Season 1 · 10 episodes · 3 watched`), so that I know where I am.
3. As an authenticated `User`, I want to expand a `Season` to see its `Episode`s (ordered `episode_number`, each with title, air date, still image, runtime, synopsis), so that I can pick an `Episode`.
4. As an authenticated `User`, I want unaired/future `Episode`s to be visible but disabled for marking as watched, so that I do not record impossible watches.
5. As an authenticated `User`, I want to mark a single `Episode` as watched via an action on its row, so that I record incremental progress.
6. As an authenticated `User`, I want the `Rating` picker (1★–5★) to become visible only after selecting an `Episode` (or `Season`), so that rating is contextual to the granular item.
7. As an authenticated `User`, I want to optionally attach a `Rating` 1–5 to an `Episode Watch` when marking watched, so that I can assess episodes individually.
8. As an authenticated `User`, I want to update the `Rating` of a watched `Episode` inline (1★–5★, current highlighted amber), so that I can correct my assessment.
9. As an authenticated `User`, I want to unmark an `Episode` as watched (remove its `Episode Watch`), so that I can fix mistakes.
10. As an authenticated `User`, I want to mark an entire `Season` as watched via a single action on the `Season` row (bulk-marks all its `Episode`s), so that I do not click each `Episode`.
11. As an authenticated `User`, I want a `Season`-level bulk mark to be transactional — either all `Episode`s become watched or none — so that progress stays consistent.
12. As an authenticated `User`, I want marking a `Season` as watched to optionally carry a `Rating` that is applied to each `Episode` created in bulk, or to leave per-episode `Rating`s null if I skip, so that bulk remains flexible.
13. As an authenticated `User`, I want the classic one-click `Mark Series as Watched` (direct `POST Library Entry status=COMPLETED`) to remain for `TV Series` when I do not care about granularity, so that backwards compatibility is preserved.
14. As an authenticated `User`, I want the `Library Entry` `Status` for a `TV Series` to be derived from granular watches: `WISHLIST` when 0 episodes watched, `IN_PROGRESS` when some but not all counted episodes watched, `COMPLETED` when all counted episodes watched or explicit series mark, so that `Status` reflects reality.
15. As an authenticated `User`, I want `Movie` `Library Entry` behavior unchanged — a single `Library Entry` with `status` + required `Rating` when `COMPLETED`, forbidden when `WISHLIST` — so that movies stay one-shot.
16. As an authenticated `User`, I want `TV Series` `Library Entry` `Rating` to be optional when granular tracking is in use (episodes carry ratings), but still allowed when I use the one-click series mark, so that both paths are valid.
17. As an authenticated `User`, I want stars on a `TV Series` `Watched` card in `GET /watched` to show the series `Rating` when present, otherwise the average of episode `Rating`s (or no stars if none rated), so that assessment is still visible.
18. As an authenticated `User`, I want the `Watched` filtered view (`GET /watched` and `GET /api/me/library?status=COMPLETED`) to list only `TV Series` where derived `Status=COMPLETED` (all counted episodes watched), not every `IN_PROGRESS` series, so that `Watched` stays meaningful.
19. As an authenticated `User`, I want progress (`watchedEpisodes/totalEpisodes/progressPercent`) exposed on every `Library Entry` for `TV Series` in list and detail, so that I can see `IN_PROGRESS` advancement outside `Watched`.
20. As an authenticated `User`, I want to navigate `Watched` cards and `Wishlist` cards to the detail page of the underlying `MediaItem` (`/media/tv/{externalId}` or `/media/movie/{externalId}`), so that I can manage there.
21. As an authenticated `User`, I want to remove a `TV Series` entirely from my library via `DELETE /api/me/library/{id}` (hard-delete) regardless of its `Season`/`Episode` watches, so that curation mirrors existing semantics.
22. As an authenticated `User`, I want deleting a series `Library Entry` to cascade-delete its `Episode Watch` rows and keep `tv_seasons`/`tv_episodes` cached for others, so that sharing the `Catalog` cache is safe.
23. As an authenticated `User`, I want marking the last unwatched `Episode` as watched to automatically promote the series `Library Entry` to `COMPLETED` (and unmarking any `Episode` to demote from `COMPLETED` to `IN_PROGRESS`), so that status stays in sync without manual steps.
24. As an authenticated `User`, I want TMDB to be the `Catalog` source for `Season`/`Episode` metadata (`tv/{id}` `seasons[]` + `tv/{id}/season/{seasonNumber}` episodes) and never need to enter season/episode data manually, so that the feature works for any series.
25. As an authenticated `User`, I want the `Catalog` season/episode data to be lazy-cached locally on first TV detail view and refreshed after 24h staleness, so that detail renders fast and survives TMDB downtime.
26. As an authenticated `User`, I want an already-cached `TV Series` to render its detail with seasons/episodes even when TMDB is unavailable (using cached `tv_seasons`/`tv_episodes` and empty `cast` fallback), so that degradation is graceful.
27. As an authenticated `User`, I want invalid `Rating` values (0, 6, non-integer) on `Episode Watch` to be rejected with `400 rating must be between 1 and 5`, and missing `rating` to be allowed (boolean watched separate from assessment), so that constraints are clear.
28. As an authenticated `User`, I want unauthenticated access to all new episode/season watch endpoints to be `401` (API) and `303 /login` (Qute), sharing the same `JwtCookieFilter` cookie→Bearer contract as existing `Library Entry` endpoints, so that auth is uniform.
29. As an authenticated `User`, I want per-`User` isolation for `Episode Watch` (I only see and can mutate my own watches; a second `User` sees 0 watched on the same series and `404`/`403` when trying to mutate my watches), so that data is private.
30. As an authenticated `User`, I want half-star ratings, watch timestamps beyond `watchedAt`, comments/notes on episodes, and `IN_PROGRESS`/`DROPPED`/`ON_HOLD` transitions to remain out of scope, so that the increment stays focused.
31. As a system, I want existing `Wishlist` and `Watched` rows (pre-migration) to remain valid with `rating = NULL` for `WISHLIST` and required for `COMPLETED` `Movie`s, without backfill of `tv_seasons`/`tv_episodes` until first TV detail visit, so that upgrade is seamless.

## Implementation Decisions

- **Domain vocabulary (respecting `CONTEXT.md` and ADRs):** Keep existing terms `User`, `Authentication`, `Email Verification`, `MediaItem` (`MediaType` `MOVIE` vs `TV_SERIES`), `Library Entry` (`User ↔ MediaItem` with `Status` + `Rating`), `Status` (`WISHLIST`, `IN_PROGRESS`, `COMPLETED`, `DROPPED`, `ON_HOLD` — `Watched` is UI label for `COMPLETED`), `Rating` (1–5 integer; `stars` is presentation), `Catalog` (TMDB). Introduce `TV Series` as a subtype of `MediaItem`, `Season` (ordered group `season_number >=0`, 0 is Specials), `Episode` (`season_number` + `episode_number` unique within series, with title/synopsis/still/air date/runtime), `Episode Watch` (`User × Episode` with boolean watched + nullable `Rating` + `watchedAt`; `Season` completeness is derived, not stored). Update glossary in `CONTEXT.md` when terms land; no implementation details in glossary.

- **ADR respect:**
  - ADR 0001 (lightweight JWT without Keycloak): reuse `smallrye-jwt` issuer `mediashelf`, `JwtCookieFilter` cookie→Bearer, `roles=User`; no new auth provider.
  - ADR 0002 (Qute + Tailwind CDN): keep Qute SSR + Tailwind CDN (`rounded-sm`/`border-zinc-200`/`shadow-sm`, `zinc`/`amber` palette); TV detail reuses same card language, season accordion uses same border/shadow, stars remain `amber-100/300/500`.
  - ADR 0003 (TMDB server proxy): keep TMDB proxied via server REST Client with `mediashelf.tmdb.*` config and key from env, never in browser; extend client with `tv/{id}/season/{seasonNumber}` and extend TV details to include `seasons[]`; cache under `catalog-detail`/`tv-seasons` 24h, 3s timeout, `502` on `5xx`/timeout, never cache `4xx`.
  - ADR 0004 (Wishlist as filtered Library Entry with lazy-cached MediaItem): preserve `library_entries` `UNIQUE(user_id, media_item_id)` and hard-delete; `MediaItem` stays lazy-cached on first detail; new `tv_seasons`/`tv_episodes` are also lazy-cached per `MediaItem` on first TV detail, sharing the same lifecycle (detail populates them, never `search`). Season/episode watches are child rows that cascade on library-entry delete but seasons/episodes themselves remain shared cache.

- **Modules built/modified:** Library persistence (add `tv_seasons`, `tv_episodes`, `episode_watches`), `Catalog` sync (season/episode fan-out and staleness), `Library Entry` derivation (TV status/rating derived from `Episode Watch`), `Episode Watch` API, Qute rendering for TV detail (`Season` accordion → `Episode` rows with Watch + Rating), `Watched`/`Wishlist` lists (progress fields), nav unchanged.

- **Schema:** Add table `tv_seasons` with unique per `MediaItem` `season_number` and fields for name, episode count, poster path, air date, timestamps; add table `tv_episodes` with unique per season `episode_number` (or per `MediaItem` `season_number+episode_number`) and fields for title, synopsis, still path, air date, runtime, timestamps; add table `episode_watches` with unique per `User` `episode_id`, nullable `rating` check 1–5, `watched_at` and timestamps, indexes on `user_id` and `episode_id`, FK cascade on episode delete but seasons/episodes themselves not cascade-deleted by user. Add index on `user_id, status, rating` still. Relax the DB constraint that `COMPLETED` must have `rating` so that it applies strictly to `Movie` or to TV without granular watches; for TV with granular watches the check is enforced in service (keeps `rating SMALLINT CHECK 1..5`). Specials `season_number=0` excluded from `totalEpisodes` counting for `COMPLETED` derivation.

- **Catalog sync & staleness:** On `GET /media/tv/{id}` for a `TV Series`, ensure `MediaItem` exists (lazy-create if needed), then sync seasons: fetch `tv/{id}` seasons summaries, then for each counted season fetch `tv/{id}/season/{n}` episodes, upsert. Use sequential or limited-parallel fan-out bounded by timeout; if TMDB fails, fall back to cached `tv_seasons`/`tv_episodes` if any (render with empty cast). Refresh if `updated_at` older than 24h. Search does not sync seasons/episodes.

- **API contracts:**
  - Series-level (`Library Entry`) stays `POST /api/me/library {externalId, mediaType, status?, rating?}`, `PATCH /api/me/library/{id} {status?, rating?}`, `GET /api/me/library?status=&page=&size=` paginated sorted `createdAt desc`, `DELETE /api/me/library/{id}` (`204`/`404`/`401`).
  - Episode-granular under the series `Library Entry`: `GET /api/me/library/{id}/seasons` returns seasons with `watchedCount/total` and progress; `GET .../seasons/{seasonNumber}/episodes` returns episodes with `watched boolean + rating`; `POST .../episodes/{episodeId}/watch {rating?:1..5}` marks watched (idempotent create), `PATCH .../watch {rating}` updates episode rating, `DELETE .../watch` unmarks; `POST .../seasons/{seasonNumber}/watch {rating?:1..5}` bulk-marks season (transactional). All return `400` for rating out of range, `404` for unknown `Library Entry`/`Season`/`Episode` or not owned, `401` if unauth, and update derived `Library Entry` status accordingly.
  - Qute: `GET /media/tv/{id}` renders season accordion and episode rows with Watch + Rating; `GET /media/movie/{id}` unchanged; `GET /watched` and `GET /wishlist` include progress fields for TV; unauth renders `303 /login?error=`.

- **Interactions:** TV detail header keeps movie-style backdrop/poster/title/synopsis/cast. Below, seasons accordion: each season card shows poster, `Season N · M episodes · K watched`, `Mark Season Watched` button (with optional star picker after selection) that bulk-creates watches; expand reveals episode rows (`E# Title — airDate — [Watched ✓/Mark] — stars 1★–5★ inline` plus still). Selecting an episode reveals its star picker; `fetch` uses `credentials:include` and `Content-Type: application/json`, reloads or patches progress on success. Movies reuse prior detail: Watched/Wishlist/Add-to-Wishlist with single star picker. `Watched`/`Wishlist` cards for TV add a progress bar/badge.

- **Config:** No new keys beyond reuse of `mediashelf.tmdb.*` (base URL, api key, image base, timeout) and `mediashelf.jwt.*`/`catalog` caches. No new property expected; timeouts shared.

- **Revision and idempotency:** Re-marking an already-watched `Episode` is idempotent (`200`); re-marking an already-fully-watched `Season` is no-op; re-adding a series `Library Entry` while one exists remains `409`.

## Testing Decisions

- **What makes a good test:** Assert external behavior at the single primary seam (HTTP JSON + Qute HTML + `Location`/`Set-Cookie`): status codes, JSON keys (`watched`, `rating`, `progressPercent`, `totalEpisodes`), HTML contains `Season`, `Episode`, `★★★★☆`, `Mark Season as Watched`, `Watched`, `Already in Wishlist`, `Location: /login`, and DB-visible effects via subsequent `GET`s. Never assert internal `TmdbClient` calls, cache internals, service method names, or repository queries. Tests are isolated per `User` (fresh email each test) and auth-gated (`401`/`303` without JWT). Each slice is also checked in a browser at `quarkus:dev`.

- **Modules tested:** Primary seam at `LibraryResource` + new `EpisodeWatchResource` + `PageResource` (QuarkusTest + RestAssured + Qute HTML). Migrations tested indirectly through that seam. Helpers reuse `TestDataHelper` extended to pre-cache `tv_seasons`/`tv_episodes` without hitting TMDB; no WireMock for TMDB in this slice — fallback to cached seasons/episodes covers it.

- **Prior art:** Same stack as `AuthFlowIntegrationTest`/`PageResourceTest`/`CatalogResourceTest`/`LibraryResourceTest`/`WatchedLibraryTest` (DevServices `postgres:16-alpine`, Flyway, Panache, SmallRye JWT, Qute). Follows `WatchedLibraryTest` patterns for `401`/`400`/`409`/`200`/`204`/`303`, pagination, isolation, nav HTML checks (`href="/watched"`), star HTML, and fallback-detail behavior. Test helper pre-caches catalog data instead of mocking TMDB.

- **Cases (incremental slices, each run before next, also browser-verified):**
  - **Slice 1 — Season/Episode view & Episode watch:** auth `401`/`303` for new episode/season endpoints and `GET /media/tv/{id}`; with pre-cached seasons/episodes, `GET /media/tv/{id}` as authed `200` contains `Season 1`, `Episode 1`, `Mark as Watched` and per-episode star picker hidden until selection; `POST .../episodes/{id}/watch` marks watched and appears as `Watched ✓` on re-get; `POST` with `rating 5` stores rating; `rating 0`/`6` → `400`; re-`POST` same episode → `200` idempotent; second `User` sees same episode unwatched and `404` on patching first user's watch; unaired episode mark → `400` or disabled.
  - **Slice 2 — Season bulk & derived status/rating:** `POST .../seasons/1/watch` marks all episodes in season; verify season progress `watched=total` and per-episode watched flags; marking all counted episodes promotes series `Library Entry` to `COMPLETED` (`GET ?status=COMPLETED` now lists it, `GET /watched` contains it with progress `N/N`); unmarking one episode demotes to `IN_PROGRESS`; series `Rating` optional when episodes drive status — series `PATCH rating` still works for movie but for TV series with episode watches `GET /watched` star average still renders; `Movie` detail still requires `Rating` for `COMPLETED`.
  - **Slice 3 — Progress, removal, isolation, pagination, nav & fallback:** progress fields on list/detail (`watchedEpisodes`, `totalEpisodes`, `progressPercent`) correct for partial Season; `DELETE /api/me/library/{id}` cascades episode watches (re-`GET` seasons show 0 watched); pagination on `GET ?status=COMPLETED` with 3 completed series still `page0 size2 total3`; `GET /media/tv/{id}` with TMDB down but cached seasons renders seasons/episodes with empty cast (no 500); `GET /app` with `Bearer` still contains `href="/wishlist"` and `href="/watched"`, unauth does not leak watched counts.

## Out of Scope

- New `Status` transitions beyond `WISHLIST`/`IN_PROGRESS`/`COMPLETED` for TV (`DROPPED`, `ON_HOLD`); watch-timestamp history, rewatch counts, notes/comments on `Episode Watch`; half-star `Rating`s, rating history/auditing.
- Persisting additional `Catalog` data beyond season/episode basics (crew/cast per episode, episode credits), image optimization, or TMDB `vote_average` import.
- Replacing Tailwind CDN with `quarkus-web-bundler`, adding Alpine/HTMX/React, new auth providers, or admin/global/social features.
- Bulk import/export or CSV sync of watches, offline-first, or alternative catalogs (OMDb/IGDB).
- Client-side optimistic UI or undo beyond `confirm` + reload; Specials `season_number=0` remains collapsed/excluded from `COMPLETED` count by design, not configurable.
- `Video Game` `MediaItem`s (`MediaType.GAME`) remain stub.

## Further Notes

- **Seam reuse:** The TV granular increment reuses the one primary seam from phases 2/3; migrations are validated only through that seam to keep the pyramid flat and avoid mocking `TmdbClient`.
- **Rating presentation:** Stars are presentation only; domain term is `Rating` 1–5, rendered `amber` (`bg-amber-100 border-amber-300 text-amber-500`) on detail and watched cards, keeping `rounded-sm`/`border-zinc-200`/`shadow-sm` per ADR 0002.
- **Progress vs Watched:** `Watched` stays the `COMPLETED` view; `Wishlist` + `Watched` plus detail progress cover `IN_PROGRESS` without a new list.
- **Caching language:** Season/episode still images use `${imageBaseUrl}/w300${stillPath}` (or `w185` fallback), season posters `w185`, movie/tv poster `w500` and backdrop `w1280` as before.
- **Naming:** Keep `posterPath`/`backdropPath`/`stillPath`, `synopsis` ↔ TMDB `overview`, `releaseDate` unified. `Episode Watch` rows carry `watchedAt` (set on create) but history beyond last watch is out of scope.
- **Isolation & idempotency:** All episode/season watch endpoints honor `JwtCookieFilter`; duplicate series `Library Entry` is `409`; duplicate episode watch is `200` no-op.
- **Browser verification:** Each slice is verified incrementally with `quarkus:dev` at `/media/tv/{id}` (e.g., id 1399) → seasons accordion → per-episode stars → bulk season mark → `/watched` progress, plus inline-edit and `DELETE`.

