# Spec: Phase 3 — Watched (COMPLETED) with 5-star Rating

## Seams (for review — please confirm)

**Proposed highest seams (fewest, widest):**

1. **HTTP API + Qute pages** — single seam covering all user-visible behavior:
   - `POST /api/me/library {externalId, mediaType, status, rating}` → creates `Library Entry` with `WISHLIST` (rating forbidden) or `COMPLETED` (rating 1–5 required)
   - `PATCH /api/me/library/{id} {status, rating}` → updates `Library Entry` (transition `WISHLIST ↔ COMPLETED`, edit `Rating`)
   - `GET /api/me/library?status=COMPLETED|WISHLIST&page=&size=` → paginated `{entries:[{id,status,rating,mediaItem}], page,size,total}` sorted `createdAt desc`
   - `DELETE /api/me/library/{id}` → `204` hard-delete
   - Qute: `GET /watched` (Watched list), `GET /media/{mediaType}/{externalId}` (detail with Watched/Wishlist state), `GET /wishlist`, `GET /app` + `base.html` nav (`Wishlist | Watched`)

   This reuses existing seams from `AuthResource` / `PageResource` / `JwtCookieFilter` and is tested at the same layer as `AuthFlowIntegrationTest` / `LibraryResourceTest` / `PageResourceTest` / `WatchedLibraryTest` (QuarkusTest + RestAssured, no unit-mocking of internals).

2. **Flyway migrations** as second seam only for persistence verification (`V4__add_rating_to_library_entries.sql` validated through the API seam above — no direct repo unit tests needed).

**No new low-level seams** (no mocked `TmdbClient` unit tests, no service-layer stubs). `CatalogService` internals including the detail fallback for cached `MediaItem`s are exercised only through the HTTP seam; failures are asserted via HTTP status + payload. If you prefer a second seam at `CatalogService`, call it out — otherwise we stay at one primary seam.

---

## Problem Statement

A `User` who has completed `Authentication` and `Email Verification` can maintain a `Wishlist` of `MediaItem`s to see later, but cannot record works they have already consumed. They have no way to register a `MediaItem` as watched with a personal assessment, no dedicated `Watched` view to browse rated items, and no way to remove a watched entry. This breaks the promised lifecycle (`wishlist → in progress → completed`) defined in `CONTEXT.md`, leaves the `Status.COMPLETED` enum unused, and forces users to remember ratings outside the tracker.

## Solution

Extend the `Library Entry` lifecycle to use `Status.COMPLETED` for watched works, always paired with a `Rating` 1–5. Provide two entry points: (a) directly mark any `MediaItem` as watched with a Rating from its detail page, and (b) transition an existing `Wishlist` entry to `COMPLETED` by choosing a Rating. Expose a direct navigation link to the `Watched` filtered view (`status=COMPLETED`) where each `Library Entry` shows its `Rating` as stars, allows inline Rating edits (1★–5★) and hard-delete via `DELETE`. Reuse the existing `Catalog` lazy-cache and `Wishlist` hard-delete semantics, keep all `Catalog` credentials server-side, and keep the UI in Qute + Tailwind CDN with the same visual language as `Wishlist`.

## User Stories

1. As an authenticated `User`, I want to mark a `MediaItem` as `Watched` directly from its detail page with a 5-star `Rating` (1–5), so that I can record works I have already consumed without first adding to `Wishlist`.
2. As an authenticated `User`, I want `Rating` to be required and integer 1–5 when `Status` is `COMPLETED`, so that my Watched data is complete and consistent.
3. As an authenticated `User`, I want the API to reject `Rating` 0 or 6 with `400 {error: "rating must be between 1 and 5"}`, so that I know the constraint.
4. As an authenticated `User`, I want to be prevented from creating a `COMPLETED` entry without a `Rating` (`400 rating required for COMPLETED`), so that I cannot create invalid data.
5. As an authenticated `User`, I want to add a `MediaItem` as `Watched` even if it was never in my `Wishlist`, so that I can capture history without prior planning.
6. As an authenticated `User`, I want to convert an existing `Wishlist` `Library Entry` to `Watched` by selecting a `Rating` (`PATCH` with `status=COMPLETED, rating=4`), so that I do not create duplicates.
7. As an authenticated `User`, I want to update the `Rating` of a `Watched` entry (e.g., 3→5) via inline stars (`PATCH {rating}`), so that I can correct my assessment.
8. As an authenticated `User`, I want to revert a `Watched` entry back to `Wishlist` (`PATCH {status=WISHLIST}`) and have its `Rating` cleared, so that I can fix a mistaken mark.
9. As an authenticated `User`, I want a direct navigation link to my `Watched` list next to `Wishlist` in the header when `currentUser` is present, so that I can find it without searching.
10. As an authenticated `User`, I want my `Watched` page (`GET /watched` and `GET /api/me/library?status=COMPLETED`) to list all my `Library Entries` where `status=COMPLETED` as cards (poster, title, `MediaType`, release year, `Rating` stars, added date), sorted newest first, paginated (20/page), so that I can browse.
11. As an authenticated `User`, I want each `Watched` card to show `Rating` as stars (`★★★★★` for 5, `★★★★☆` for 4, `★★★☆☆` for 3, `★★☆☆☆` for 2, `★☆☆☆☆` for 1) and `(n/5)`, so that I can see my assessment at a glance.
12. As an authenticated `User`, I want each `Watched` card to have 1★–5★ buttons to change `Rating` inline via `PATCH`, with the current `Rating` highlighted (amber), so that editing is instant.
13. As an authenticated `User`, I want each `Watched` entry to have a “Remove” action that calls `DELETE /api/me/library/{id}` and returns `204`, so that I can curate the list.
14. As an authenticated `User`, I want removal to be immediate in the UI (card disappears after confirm dialog, page reload) with no undo required, so that curation feels intentional.
15. As an authenticated `User`, I want hard-delete to allow re-adding the same `MediaItem` later as `WISHLIST` or `COMPLETED`, so that curation is reversible.
16. As an authenticated `User`, I want each `Watched` entry to have a link back to its detail page (`/media/{mediaType}/{externalId}`), so that I can re-inspect before removing.
17. As an authenticated `User`, I want the detail page to show a “Watched” amber badge + current `Rating` stars (`★★★★☆ (4/5)`) + “Update rating” 1–5 picker + “Remove from Watched” when `alreadyInWatched`, so that I can manage there.
18. As an authenticated `User`, I want the detail page to show “Already in Wishlist” + “Mark as Watched” 1–5 picker when `alreadyInWishlist`, so that I can transition without duplicate POST.
19. As an authenticated `User`, I want the detail page to show both “Add to Wishlist” and “Or mark as Watched” 1–5 picker when no `Library Entry` exists, so that I have both entry points.
20. As an authenticated `User`, I want duplicate `POST` for the same `(externalId, mediaType)` to be rejected with `409` (`already in wishlist` or `already in library` for `COMPLETED`), regardless of target `Status`, so that I do not duplicate `Library Entries` (`UNIQUE(user_id, media_item_id)`).
21. As an authenticated `User`, I want my `Watched` list isolated per `User` (I only see my `Library Entries`), so that my data is private.
22. As an authenticated `User`, I want a second `User` to see an empty `Watched` when I have entries, and to receive `404` if they try to `DELETE` my `Library Entry`, so that isolation is enforced.
23. As an authenticated `User`, I want unauthenticated access to `POST`/`PATCH`/`GET`/`DELETE /api/me/library`, `GET /watched`, `GET /media/*` to be `401`/`303` to `/login`, so that the `Catalog` and `Library Entry` cannot be abused.
24. As an authenticated `User`, I want `Wishlist` entries to never have a `Rating` (`POST WISHLIST` with `rating` → `400 rating not allowed for WISHLIST`, `PATCH WISHLIST` with `rating` → `400`), so that the domain stays consistent.
25. As an authenticated `User`, I want `PATCH` with no `status` nor `rating` to be `400 status or rating must be provided`, so that the API is safe.
26. As an authenticated `User`, I want `Watched` pagination (`page & size & total`, `size` clamped 1–100) to work like `Wishlist` (e.g., 3 entries with `size=2` → `page0:2, page1:1`), so that large libraries are navigable.
27. As an authenticated `User`, I want the header to show `Wishlist` and `Watched` links only when `currentUser != null`, otherwise `Login`/`Register`, so that the anonymous view is clean.
28. As an authenticated `User`, I want the empty `Watched` page to explain that I can mark items as watched with a `Rating` from `/app` or detail pages, so that I know how to populate it.
29. As a system, I want existing `Wishlist` rows (pre-migration) to remain valid with `rating = NULL` after the new migration, so that the upgrade is seamless.

## Implementation Decisions

- **Seams:** Single primary seam at HTTP API + Qute pages (see Seams section); Flyway migration as secondary seam only. No new mocked `TmdbClient` or service stubs; all behavior verified via `Bearer`/cookie auth through `JwtCookieFilter` like `AuthFlowIntegrationTest`.
- **Modules built/modified:** Library persistence (add `Rating` to `Library Entry`), Catalog detail fallback for cached `MediaItem`s, Library API, Page rendering for `Watched` and detail state, Qute templates (`watched.html`, `catalog/detail.html` star pickers, `base.html` nav). Existing `security/JwtCookieFilter`, `service/JwtService`, `AuthService` untouched beyond sharing the same auth contract.
- **Domain vocabulary:** Strictly use `CONTEXT.md` language — `MediaItem` (`MediaType` `MOVIE|TV_SERIES`), `Library Entry` (`User ↔ MediaItem` with `Status` and `Rating`), `Status` (`WISHLIST` vs `COMPLETED`; `IN_PROGRESS/DROPPED/ON_HOLD` reserved), `Wishlist` (`status=WISHLIST` view; `Backlog` alias), `Watched` (`status=COMPLETED` view; `Completed` is domain term, `Watched` is UI label), `Rating` (1–5 integer; avoid `vote/score`; `stars` is presentation), `Catalog` (TMDB, external, never in browser).
- **ADR respect:**
  - ADR 0001 (lightweight JWT without Keycloak): Reuse `quarkus-smallrye-jwt` issuer `mediashelf`, `JwtCookieFilter` cookie→Bearer, `roles=User`. No new auth provider.
  - ADR 0002 (Qute + Tailwind CDN): Keep Qute SSR with Tailwind CDN (`rounded-sm`/`border`/`shadow-sm`, `zinc` palette, `Inter`/`JetBrains Mono`), no `web-bundler`, no DaisyUI. `Watched` cards reuse `Wishlist` card styling; stars in `amber` (`amber-100/300/500`) for distinction.
  - ADR 0003 (TMDB server proxy): Keep TMDB `https://api.themoviedb.org/3` proxied via REST Client with `mediashelf.tmdb.*` config, key from `%prod` env, never exposed to browser. Detail still fetches `/{movie|tv}/{id}` + `/{movie|tv}/{id}/credits` in parallel when possible; add fallback to locally cached `MediaItem` (no credits) when TMDB is unavailable so that detail for already-cached items renders without re-hitting TMDB (enables tests with pre-cached items and graceful degradation).
  - ADR 0004 (Wishlist as filtered Library Entry with lazy-cached MediaItem): Keep `MediaItem` lazy-cached on first detail; `Watched` reuses same `UNIQUE(user_id, media_item_id)` — a `MediaItem` can only have one `Library Entry` per `User`, so `Wishlist → Watched` is a `PATCH` transition, not a new row. Hard-delete on `DELETE` for both views; no soft-delete yet.
- **Schema:** New migration `V4__add_rating_to_library_entries.sql` — `rating SMALLINT CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)`, `CHECK (status <> 'COMPLETED' OR rating IS NOT NULL)`, `CHECK (status <> 'WISHLIST' OR rating IS NULL)`, index `(user_id, status, rating)`. Validates that `Completed` must carry `Rating`, `Wishlist` must not, and allows future `Status` values to be nullable. Existing `WISHLIST` rows stay `NULL`.
- **API contracts:**
  - `POST /api/me/library` (auth, `Content-Type: application/json`, body `{externalId: Long! , mediaType: "movie"|"tv"!" , status?: "WISHLIST"|"COMPLETED" , rating?: 1..5 }`) → `201 {id, status, mediaItem:{id,externalId,mediaType,title,synopsis,posterPath,backdropPath,releaseDate}, createdAt, rating}` or `400` for missing/invalid `rating` per status, `409` if `(userId, mediaItemId)` exists (`already in wishlist` for `WISHLIST`, `already in library` for `COMPLETED`), `404` if `MediaItem` not found and Catalog cannot lazy-create, `401` if unauth.
  - `PATCH /api/me/library/{id}` (auth, body `{status?, rating?}` at least one required) → `200` updated entry (same shape as `POST`), `400` for `status or rating must be provided`, invalid `rating`, `rating required for COMPLETED`, `rating not allowed for WISHLIST`, `404` if not owned, `401` if unauth.
  - `GET /api/me/library?status=WISHLIST|COMPLETED&page=0&size=20` (auth, defaults `WISHLIST`, `page>=0`, `size` clamped 1–100) → `200 {entries:[...], page,size,total}` sorted `createdAt desc`.
  - `DELETE /api/me/library/{id}` (auth) → `204` hard-delete, `404` if not owned, `401` if unauth.
  - Qute: `GET /watched` renders `watched.html` (auth-gated, `302 /login?error=` if unauth, otherwise `entries, total, currentUser`); `GET /media/{type}/{id}` renders `catalog/detail.html` with `mediaItem, cast, director, posterUrl/backdropUrl, alreadyInWishlist/alreadyInWatched, currentStatus/currentRating/currentEntryId, currentUser` (fallback to cached `MediaItemDto` with empty `cast/director` when TMDB fails but `MediaItem` is cached; `404` if not found).
- **Config:** No new `application.properties` keys; reuse `mediashelf.tmdb.base-url/api-key/image-base-url/timeout` and `mediashelf.jwt.*`. `mediashelf.tmdb.api-key` remains `test-key` in `%test`.
- **Interactions:**
  - `base.html` header shows `Wishlist` + `Watched` links when `currentUser != null`.
  - `watched.html` grid: poster (`image.tmdb.org/t/p/w185`), title links to detail, `MediaType` + year, `Rating` stars + `(n/5)`, 1★–5★ inline `rate-btn` (`PATCH {rating}` via `fetch` with `credentials:include`, `Content-Type: application/json`), `added {createdAt}`, `Remove` (`DELETE` with `confirm`, then reload).
  - `catalog/detail.html`: state-driven. If `COMPLETED`: amber `Watched` badge + `★★★★☆ (4/5)` + 5-star `watched-star` (current highlighted `bg-amber-100 border-amber-300`) → `PATCH {rating}` + `Remove from Watched` (`DELETE`). If `WISHLIST`: `Already in Wishlist` + 5-star → `PATCH {status:COMPLETED, rating}`. If none: `Add to Wishlist` (`POST {externalId, mediaType}`) + “Or mark as Watched” 5-star → `POST {externalId, mediaType, status:COMPLETED, rating}`. All `fetch` use `credentials:include` and reload on `201/200`. JS avoids Qute interpolation by building payload via property assignment (`payload.rating = rating`) rather than shorthand `{rating}`.
  - Pagination: both `Wishlist` and `Watched` use same `page/size/total` contract, `size` capped 100.
- **Caching & resilience:** Reuse existing `catalog-search` (10 min) and `catalog-detail` (24 h) caches; detail parallel fetch with 3s timeout; `502` on TMDB `5xx`/timeout; never cache `4xx`. `Watched` list itself is not cached (always `user_id, status` query).

## Testing Decisions

- **What makes a good test:** Assert external HTTP/Qute behavior (status, JSON keys including `rating`, HTML contains `★★★★★`/`(5/5)`/`href="/watched"`, `Location`/`Set-Cookie`, DB-visible effects via `GET` list) — never assert internal `TmdbClient` calls, service method names, or cache internals. Tests must be isolated per `User` (fresh email per test) and auth-gated (`401`/`303` if no JWT). Each increment is verified directly in the browser-equivalent (Qute HTML via `GET /watched`, `GET /media/{type}/{id}`, `GET /app` with `Authorization: Bearer`).
- **Modules tested:** Primary seam at `LibraryResource`/`PageResource` (QuarkusTest + RestAssured + Qute HTML assertions) plus `PageResource` detail fallback. Migrations tested indirectly through the API seam (no direct repo unit tests). Prior art is `AuthFlowIntegrationTest` / `AuthResourceTest` / `PageResourceTest` / `CatalogResourceTest` (same stack: `postgres:16-alpine` DevServices, Flyway, Panache, SmallRye JWT, Qute). New prior art is `WatchedLibraryTest` and `TestDataHelper` for pre-caching `MediaItem`s without hitting TMDB.
- **Cases (via HTTP/Qute seam, incremental slices — each slice adds tests and is run before next slice, also checked in browser at `http://localhost:8080` in `quarkus:dev`):**
  - **Slice 1 — API `COMPLETED`+`Rating`:** `401` if unauth for `POST`/`PATCH`/`GET`/`DELETE`; `POST COMPLETED` with `rating 5` → `201` with `rating 5`; `POST COMPLETED` missing `rating` → `400 rating required`; `rating 0`/`6` → `400 between 1 and 5`; `POST WISHLIST` with `rating` → `400 not allowed`; duplicate `POST` same `(user, mediaItem)` → `409`; `GET ?status=COMPLETED` contains `rating`, `GET ?status=WISHLIST` does not; `GET /watched` `200` contains `★★★★★` and `href="/media/movie/{id}"`.
  - **Slice 2 — Transitions & edits:** `PATCH {rating:5}` on `COMPLETED` → `200` persists; `PATCH {rating:0}` or `{}` → `400`; `POST WISHLIST` then `PATCH {status:COMPLETED, rating:4}` → moves from `Wishlist` to `Watched` (Wishlist size 0, Watched size 1, `GET /watched` shows `★★★★☆`); `PATCH {status:WISHLIST}` on `COMPLETED` clears `rating` to `null` and moves back.
  - **Slice 3 — Removal, isolation, pagination, detail & nav:** `DELETE` → `204` removes from `GET ?status=COMPLETED` and `GET /watched` (no `★★☆☆☆`); second `DELETE` → `404`; second `User` sees empty `Watched` and `404` on deleting first `User`'s entry; pagination `page=0,size=2` with 3 entries → `total 3, entries 2` and `page=1,size=2` → `1`; `GET /media/movie/{id}` when `COMPLETED` → `200` contains `Watched` + `★★★★☆` + `Update rating` + `Remove from Watched`; when `WISHLIST` → `Already in Wishlist` + `Mark as Watched`; when none → `Add to Wishlist` + `Or mark as Watched`; `GET /app` with `Bearer` contains `href="/wishlist"` and `href="/watched"` + `>Watched<`, `GET /` without auth does not.
  - **Auth & isolation:** All new `api/me/library` and `watched`/`media` Qute pages require `Bearer`/cookie via `JwtCookieFilter`; Qute unauth → `303 /login?error=`.
- **Out-of-protocol:** No unit tests for star string building or `posterPath` concatenation, no mocked `EntityManager`; rely on DevServices Postgres as in existing tests. `TestDataHelper` (`createMediaItem`) is test-only helper to pre-cache `MediaItem`s and avoid TMDB calls; not a production seam. No WireMock for `Catalog` in this slice — detail fallback to cached `MediaItem` (empty cast) is used instead.

## Out of Scope

- Video Game `MediaItem`s (`MediaType.GAME`), `Status` values beyond `WISHLIST` and `COMPLETED` (`IN_PROGRESS`, `DROPPED`, `ON_HOLD`) and their transitions, half-star `Rating`s (e.g., 4.5), `Rating` history/auditing, and `Library Entry` soft-delete.
- Persisting `cast`/`director` in DB, progress percentage, personal notes/comments on entries, or TMDB `vote_average` import.
- Offline-first, bulk import/export of `Watched`/`Wishlist`, or CSV/JSON sync.
- Replacing Tailwind CDN with `quarkus-web-bundler`, adding Alpine/HTMX/React, or new auth providers (Google OAuth, Keycloak) — deferred per ADR 0001/0002.
- Admin moderation, global trending lists, or social features between `Users` (sharing Watched, likes, follows).
- Alternative catalog providers (OMDb, IGDB) or changing the TMDB proxy contract; search remains `search/multi` server-side.
- Client-side optimistic UI or undo for removal (current behavior is `confirm` + reload).

## Further Notes

- **Seam reuse:** The `Watch` increment reuses the single primary seam (`HTTP API + Qute`) from phase 2; `V4` migration is validated only through that seam. This keeps the test pyramid flat and avoids mocking `TmdbClient`/`CatalogService`.
- **Rating presentation:** Stars are presentation only — domain term is `Rating` (1–5). Watched cards and detail use `amber` (`bg-amber-100 border-amber-300 text-amber-500/600`) to distinguish from `Wishlist` zinc styling, while still using `rounded-sm`/`border-zinc-200`/`shadow-sm` per ADR 0002.
- **Image URLs:** Built as `${imageBaseUrl}/w500${posterPath}` for cards/poster, `w1280` for backdrop, `w185` for cast `profilePath` (when present via TMDB; empty in fallback).
- **Naming:** Keep `posterPath`/`backdropPath` (TMDB `poster_path`/`backdrop_path`), `synopsis` ↔ TMDB `overview`, `releaseDate` unified across `release_date` (Movie) / `first_air_date` (TV). `Watched` is the UI label; `COMPLETED` is the persisted `Status`.
- **Isolation & idempotency:** All new endpoints honor `JwtCookieFilter` cookie→Bearer so Qute `POST /login` flow and `Bearer` API flow share the same `Library Entry` isolation (`user_id, media_item_id` unique). Re-adding after hard-delete is allowed; duplicate while present is `409`.
- **Browser verification:** Each slice is verified incrementally with `./mvnw quarkus:dev` at `http://localhost:8080/app` → search → detail → 1–5★ Watched, `GET /watched` stars, inline edit, Remove, and `GET /wishlist` unchanged. Tests mirror this with `GET /watched`/`GET /media/*` HTML assertions (`containsString`).
