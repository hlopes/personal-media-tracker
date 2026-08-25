# Spec: Phase 2 — Catalog Search, Media Detail & Wishlist (TMDB)

> **Tracker note:** `docs/agents/issue-tracker.md` / `triage-labels.md` not found — please run `/setup-matt-pocock-skills` to configure the issue tracker (defaults: GitHub `hlopes/personal-media-tracker` + labels `needs-triage|needs-info|ready-for-agent|ready-for-human|wontfix`). Draft saved locally as `.scratch/spec-phase2-wishlist-catalog.md` and will be published with `ready-for-agent` once tracker is configured.

## Seams (for review — please confirm)

**Proposed highest seams (fewest, widest):**

1. **HTTP API + Qute pages** — single seam covering all user-visible behavior:
   - `GET /api/catalog/search?q=&type=&page=` (auth, proxied TMDB `search/multi`, cached)
   - `GET /api/catalog/{mediaType}/{externalId}` (auth, detail + credits, lazy-caches `MediaItem`)
   - `POST /api/me/library {externalId, mediaType}` → `WISHLIST` `Library Entry`; `GET /api/me/library?status=WISHLIST&page=&size=`; `DELETE /api/me/library/{libraryEntryId}`
   - Qute: `GET /app` (search box), `GET /media/{type}/{id}`, `GET /wishlist` (or `/backlog` alias) + `base.html` nav

This reuses existing seams from `AuthResource` / `PageResource` / `JwtCookieFilter` (`src/main/java/org/hlopes`) and is tested at the same layer as `AuthFlowIntegrationTest` / `AuthResourceTest` / `PageResourceTest` (QuarkusTest + RestAssured, no unit-mocking of internals).

2. **Flyway migrations** as second seam only for persistence verification ( `V2__create_media_items.sql` + `V3__create_library_entries.sql` validated through the API seam above — no direct repo unit tests needed).

**No new low-level seams** (no mocked `TmdbClient` unit tests, no service-layer stubs). `TmdbClient`/`CatalogService` internals are exercised only through the HTTP seam; failures are asserted via HTTP status + payload. If you prefer a second seam at `CatalogService`, call it out — otherwise we stay at one primary seam.

---

## Problem Statement

A `User` who has completed `Authentication` and `Email Verification` can log into the app (`GET /app`) but cannot discover or track new works. They must manually remember movies/TV series they want to see; there is no searchable `Catalog`, no detail view, and no personal `Wishlist` to collect `MediaItem`s for later watching. This breaks the core promise of a personal media tracker (wishlist → in progress → completed) defined in `CONTEXT.md:1`.

## Solution

Provide an authenticated, server-proxied `Catalog` search (TMDB) directly in the app. After `Authentication`, the `User` sees a search box in `GET /app`; typing shows a dropdown of matching `MediaItem`s. Selecting a result opens a detail page with title, synopsis, poster (`posterPath`), backdrop (`backdropPath`), release date, cast (top 10) and director, plus an “Add to Wishlist” action that creates a `Library Entry` with `Status=WISHLIST`. A link in the account menu (`base.html`) opens the `Wishlist` (`GET /wishlist` / `GET /api/me/library?status=WISHLIST`) where the `User` can view all their `Library Entry`s and remove any (hard-delete in phase 2). All `Catalog` credentials stay server-side, exposed via typed `ApplicationConfig` (`mediashelf.tmdb.*`).

## User Stories

1. As an authenticated `User`, I want a search box in the app (`GET /app`) so that I can look up movies/TV series without leaving the tracker.
2. As an authenticated `User`, I want my search to be debounced and require ≥2 characters before querying, so that I don’t spam the `Catalog`.
3. As an authenticated `User`, I want a dropdown under the search input showing up to 5 matching `MediaItem`s (title, `MediaType`, release year, poster thumbnail), so that I can quickly identify the right work.
4. As an authenticated `User`, I want dropdown results to update as I type and to be keyboard-navigable (↑/↓, Enter to select, Esc to close), so that I can search efficiently.
5. As an authenticated `User`, I want to click/Enter a dropdown result to open its detail page (`GET /media/{mediaType}/{externalId}`), so that I can evaluate it.
6. As an authenticated `User`, I want the detail page to show title (Movie `title` / TV `name`), synopsis (`overview`), cover image (`posterPath`), background image (`backdropPath`), release date (`release_date` / `first_air_date`), so that I have core metadata.
7. As an authenticated `User`, I want the detail page to show cast (top 10 `cast` with name, character, profile) and director (Movie: `crew.job=Director`; TV: `created_by` or Director), so that I have credits context.
8. As an authenticated `User`, I want a single “Add to Wishlist” button on the detail page, so that I can add the `MediaItem` to my `Wishlist` in one click.
9. As an authenticated `User`, I want the “Add to Wishlist” button to be disabled / show “Already in Wishlist” if my `Library Entry` for that `MediaItem` already exists, so that I don’t create duplicates.
10. As an authenticated `User`, I want adding to return `201` with the new `Library Entry` and to show a success state, so that I have confirmation.
11. As an authenticated `User`, I want adding a duplicate `MediaItem` to be idempotent and return `409 {error: "already in wishlist"}`, so that the API is safe to retry.
12. As an authenticated `User`, I want unauthenticated access to `/api/catalog/*`, `/api/me/library`, `/media/*`, `/wishlist` to redirect/`401` to login, so that the `Catalog` cannot be abused.
13. As an authenticated `User`, I want my `Wishlist` isolated per `User` (I only see my `Library Entry`s), so that my data is private.
14. As an authenticated `User`, I want a link to my `Wishlist` in the account menu/nav (`base.html` when `currentUser != null`), so that I can find it.
15. As an authenticated `User`, I want my `Wishlist` page (`GET /wishlist` and `GET /api/me/library?status=WISHLIST`) to list all my `Library Entry`s as cards (poster, title, `MediaType`, release year, added date), sorted newest first, paginated (20/page), so that I can browse.
16. As an authenticated `User`, I want each `Wishlist` entry to have a “Remove” action that calls `DELETE /api/me/library/{libraryEntryId}` and returns `204`, so that I can curate the list.
17. As an authenticated `User`, I want removal to be immediate in the UI (card disappears) with undo not required, so that curation feels instant.
18. As an authenticated `User`, I want `Wishlist` removal to hard-delete the `Library Entry` (phase 2) — not merely hide it — so that I can re-add the same `MediaItem` later.
19. As an authenticated `User`, I want `Wishlist` entries to show a link back to their detail page, so that I can re-inspect before removing.
20. As an authenticated `User`, I want search with no results to show “No results for ‘…’” in the dropdown, so that I know the query was processed.
21. As an authenticated `User`, I want detail for an unknown `externalId`/`mediaType` to return `404` (Qute shows “Media not found”), so that broken links are clear.
22. As an authenticated `User`, I want `Catalog` failures (TMDB down, rate-limited, timeout) to surface as `502/504` with a friendly message and not corrupt local data, so that the app degrades gracefully.
23. As an authenticated `User`, I want search to be available both as `GET /api/catalog/search?type=multi` (default, 5 movie + 5 TV merged) and filtered `type=movie|tv`, so that I can narrow results.
24. As a system, I want search results cached (~10 min per normalized query) and detail/credits fetched in parallel, so that TMDB rate limits (40 req/10s) are respected and detail is fast.

## Implementation Decisions

- **Modules built/modified:** New `catalog/` (TmdbClient, CatalogService, dto mappers), `media/` (MediaItem entity/repository), `library/` (LibraryEntry entity/repository), `resource/CatalogResource` + `LibraryResource`; extend `config/ApplicationConfig` with `Tmdb` nested mapping, `resource/PageResource` with `GET /media/{type}/{id}` + `GET /wishlist`, `templates/` (search dropdown JS, `catalog/detail.html`, `wishlist.html`, nav link in `base.html`), `application.properties` + Flyway `V2`/`V3`. Existing `security/JwtCookieFilter`, `service/JwtService`, `AuthService` untouched beyond config wiring.
- **Domain vocabulary:** Strictly use `CONTEXT.md:1` language — `MediaItem` (with `MediaType` `MOVIE|TV_SERIES`), `Library Entry` (association `User↔MediaItem` with `Status`), `Status.WISHLIST` (only status in phase 2), `Wishlist` (view where `status=WISHLIST`; UI term “Backlog” is alias, not glossary), `Catalog` (TMDB, external, never in browser). UI terms “cover/background image” become `posterPath`/`backdropPath` (TMDB `poster_path`/`backdrop_path`); “casting/director” become `credits.cast[0..9]` + `director` (Movie `crew.job=Director`, TV `created_by[0]` or crew Director).
- **ADR 0003 — TMDB server proxy:** Chose TMDB v3 proxied via `quarkus-rest-client-reactive-jackson` (`mediashelf.tmdb.base-url=https://api.themoviedb.org/3`, `api-key=${TMDB_API_KEY}`, `image-base-url=https://image.tmdb.org/t/p`). Key from `ApplicationConfig.Tmdb.apiKey()` (`%prod` env override), never exposed to Qute/JS. Rejected browser-direct TMDB and OMDb (key exposure, weaker TV data).
- **ADR 0004 — Lazy-cached MediaItem + hard-delete Wishlist:** Search does not persist; detail `GET /api/catalog/{type}/{id}` fetches `/{movie|tv}/{id}` + `/{movie|tv}/{id}/credits` in parallel, then `findOrCreateMediaItem(externalId, mediaType)` snapshots title/synopsis/poster/backdrop/releaseDate. `POST /api/me/library {externalId, mediaType}` reuses that item and creates `Library Entry(status=WISHLIST)`. `GET /api/me/library` filters by `status`; `DELETE` hard-deletes (no `DROPPED` yet). Cast/director are returned with detail but not persisted beyond optional 24h cache. Rejected “persist every search result” (write amplification) and “store only externalId” (slow backlog reads).
- **API contracts:**
  - `GET /api/catalog/search?q=term&type=multi|movie|tv&page=1` (auth required, q ≥2, returns `{results:[{externalId, mediaType, title, posterPath, releaseDate, overview}], page, total}` capped 10, UI shows 5; 400 if q missing/short, 502 on Catalog error).
  - `GET /api/catalog/{mediaType}/{externalId}` (auth, returns `{mediaItem:{id,externalId,mediaType,title,synopsis,posterPath,backdropPath,releaseDate}, credits:{cast:[{name,character,profilePath}], director:{name}}}`; 404 if not found, 502 on Catalog error).
  - `POST /api/me/library` (auth, body `{externalId, mediaType}`) → `201 {id, status, mediaItem}` or `409` if `(userId, mediaItemId)` already exists; validates `mediaType` enum.
  - `GET /api/me/library?status=WISHLIST&page=1&size=20` (auth, paginated `{entries:[...], page, size, total}` sorted `createdAt desc`).
  - `DELETE /api/me/library/{libraryEntryId}` (auth, `204` or `404` if not owned).
  - Qute: `GET /app` adds search box (auth-gated), `GET /media/{type}/{id}` renders `catalog/detail.html`, `GET /wishlist` renders `wishlist.html` (both redirect to `/login` if unauthenticated via `PageResource` cookie→Bearer check).
- **Schema:** `V2__create_media_items.sql` — `id UUID PK`, `external_id BIGINT NOT NULL`, `media_type VARCHAR(20) NOT NULL CHECK (MOVIE|TV_SERIES)`, `title VARCHAR NOT NULL`, `synopsis TEXT`, `poster_path VARCHAR`, `backdrop_path VARCHAR`, `release_date DATE`, `created_at TIMESTAMPTZ`, `UNIQUE(external_id, media_type)`. `V3__create_library_entries.sql` — `id UUID PK`, `user_id UUID NOT NULL FK users(id)`, `media_item_id UUID NOT NULL FK media_items(id)`, `status VARCHAR(20) NOT NULL CHECK (WISHLIST|IN_PROGRESS|COMPLETED|DROPPED|ON_HOLD)`, `created_at TIMESTAMPTZ`, `UNIQUE(user_id, media_item_id)`, `INDEX(user_id, status)`. Entities use `PanacheEntityBase`, `CamelCaseToUnderscoresNamingStrategy`.
- **Config:** `application.properties` adds `mediashelf.tmdb.api-key=${TMDB_API_KEY}`, `mediashelf.tmdb.base-url`, `mediashelf.tmdb.image-base-url`, `mediashelf.tmdb.timeout` (with `%prod` overrides); `ApplicationConfig.java` gets `Tmdb tmdb()` with `apiKey()`, `baseUrl()`, `imageBaseUrl()`, `timeout()` (`@WithDefault`). Also `mediashelf.jwt.issuer/lifespan` aliases remain (`application.properties:65`).
- **Interactions:** `app.html` header search input (vanilla JS, 300ms debounce, `fetch` with cookie/Bearer, renders dropdown `rounded-sm`/`border-zinc-200`/`shadow-sm`, zinc palette, Inter/JetBrains Mono, keyboard nav). Dropdown select → `location=/media/{type}/{id}`. Detail Qute embeds poster (`imageBaseUrl/w500`), backdrop (`w1280`), add button (`POST`), wishlist cards reuse same styling. Nav in `base.html` shows `Wishlist` link when `currentUser != null`.
- **Caching & resilience:** Caffeine/In-memory 10 min for `search?q+type+page` normalized (lowercased, trimmed); 24h for detail snapshot; detail parallel fetch with 3s timeout; `502` on TMDB `5xx`/timeout, `504` on gateway timeout; never cache `4xx`.
- **Auth:** All new `api/catalog` + `api/me/library` require `Bearer`/cookie via `JwtCookieFilter`; Qute pages use `PageResource` auth check → `302 /login?error=`.

## Testing Decisions

- **What makes a good test:** Assert external HTTP/Qute behavior (status, JSON keys, HTML contains, `Location`/`Set-Cookie`, DB-visible effects) — never assert internal `TmdbClient` calls, service method names, or cache internals. Tests must be isolated per `User` (fresh email per test) and auth-gated (401/302 if no JWT).
- **Modules tested:** Primary seam at `CatalogResource`/`LibraryResource`/`PageResource` (QuarkusTest + RestAssured + Qute HTML assertions). Migrations tested indirectly through those seams (no direct repo unit tests). Prior art is `AuthFlowIntegrationTest` / `AuthResourceTest` / `PageResourceTest` (same stack: `postgres:16-alpine` DevServices, Flyway, Panache, SmallRye JWT).
- **Cases (via API seam):**
  - Search: 401 if unauth, 400 if `q` missing/<2 chars, 200 with 5+ results for “matrix” (type multi), filter by `type=movie` vs `tv`, cache hit returns same shape, `502` simulated via WireMock `TmdbClient` stub (test profile overrides `mediashelf.tmdb.base-url` to WireMock).
  - Detail: 401 if unauth, 404 if unknown TMDB id, 200 returns `mediaItem` + `credits` (cast 10, director), second fetch hits `media_items` snapshot (no TMDB call), TV vs Movie title/date mapping.
  - Library add: 401 if unauth, `201` creates `Library Entry`, duplicate `POST` → `409`, invalid `mediaType` → `400`, entry appears in `GET /api/me/library?status=WISHLIST`, not visible to second `User`.
  - List/remove: pagination `page/size` + `total`, sorted `createdAt desc`, `DELETE` → `204` removes from list, second `DELETE` → `404`, unauth `DELETE` → `401`.
  - Qute: `GET /app` contains search box when authed, `GET /media/movie/{id}` renders title/poster/director/cast + add button, `GET /wishlist` renders cards + remove buttons, unauth `GET /wishlist` → `302 /login`.
  - WireMock for `Catalog` is test-only (quarkus mock server), not a new production seam.
- **Out-of-protocol:** No unit tests for `PosterPath` string building, no mocked `EntityManager`; rely on DevServices Postgres as in existing tests.

## Out of Scope

- Video Game `MediaItem`s (still stub), `MediaType.GAME`.
- `Status` transitions beyond `WISHLIST` (`IN_PROGRESS`, `COMPLETED`, `DROPPED`, `ON_HOLD`), ratings, progress, notes, and `Library Entry` soft-delete/auditing.
- Persisting `cast`/`director` in DB, user ratings, or personal notes on entries.
- Offline-first or bulk import/export of `Wishlist`.
- Replacing Tailwind CDN with `quarkus-web-bundler`, adding Alpine/HTMX/React, or new auth providers (Google OAuth, Keycloak) — deferred per `ADR 0001`/`ADR 0002`.
- Admin moderation, global trending lists, or social features between `User`s.
- TMDB alternative providers (OMDb, IGDB) in this slice.

## Further Notes

- TMDB image URLs are built as `${imageBaseUrl}/${size}${posterPath}` (`w500` for cards/poster, `w1280` for backdrop, `w185` for cast `profilePath`).
- Naming: keep `posterPath`/`backdropPath` (not “cover/background image”), `synopsis` maps to TMDB `overview`, `releaseDate` unified across `release_date` (Movie) / `first_air_date` (TV).
- All new endpoints honor `JwtCookieFilter` cookie→Bearer so `POST /login` Qute flow and `Bearer` API flow share the same `Library Entry` isolation.
- Idempotency for `MediaItem` is by `UNIQUE(externalId, mediaType)`; `Library Entry` by `UNIQUE(userId, mediaItemId)`.

