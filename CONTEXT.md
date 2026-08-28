# Personal Media Tracker

A personal system for tracking consumption of movies and TV shows across a full lifecycle (wishlist → in progress → completed).

## Language

**User**:
A person who owns a library of media items, identified uniquely by email address. A User authenticates with a password and must verify ownership of their email.
_Avoid_: Account, customer, profile

**Authentication**:
The process of proving a User's identity via local credentials (email + password) and issuing a signed JWT for subsequent API access.
_Avoid_: Login alone, session, token as synonym

**Email Verification**:
A step where a User proves control of their email address by following a one-time verification link/token sent to that address before gaining full access.
_Avoid_: Email confirmation as separate concept, activation

**MediaItem**:
A single trackable work — a Movie or TV Series — cached locally from an external Catalog, identified by `(externalId, mediaType)` and holding title, synopsis, poster/backdrop paths and release date. Video Game remains stub for phase 2.
_Avoid_: Media, entry, title (overloaded)

**MediaType**:
The kind of a MediaItem: `MOVIE` or `TV_SERIES` (from TMDB `movie`/`tv`). Determines which title/date field is canonical.
_Avoid_: Type alone, category

**TV Series**:
A subtype of `MediaItem` where `mediaType = TV_SERIES`, composed of ordered `Season`s each containing `Episode`s. A `TV Series` is tracked via a single `Library Entry` at series level, with progress derived from `Season Watch` rows (one per `Season`).
_Avoid_: Show, serial as synonym

**Season**:
An ordered grouping of `Episode`s within a `TV Series`, identified by `season_number >= 0` (0 is Specials). A `Season` carries name, poster path, air date and episode count from the `Catalog`, cached locally on first TV detail. Watch state for a `Season` lives in `Season Watch`, not in `Season` itself. Specials (`0`) is collapsed by default and excluded from `Completed` progress.
_Avoid_: Series as synonym, volume

**Episode**:
A single broadcast unit within a `Season`, uniquely identified within its `TV Series` by `season_number` + `episode_number` and ordered by `episode_number`. Holds title, synopsis, still path, air date and runtime from the `Catalog`. An `Episode` is not a `MediaItem` and is not directly watchable; progress is tracked only at its parent `Season`.
_Avoid_: Chapter, installment

**Season Watch**:
The association between a `User` and a `Season` indicating the whole `Season` has been watched. Presence of the row means watched; it may carry a nullable `Rating` 1–5 and `watchedAt` set on creation. `Season` completeness and `Library Entry` `Status` for a `TV Series` are derived from the set of `Season Watch` rows, counting only `Season`s with `season_number != 0` and rejecting watches for any `Season` containing unaired `Episode`s or with a future `airDate`.
_Avoid_: Watched season as entity name, viewing log

**Library Entry**:
The association between a User and a MediaItem, holding the User-specific lifecycle state. In phase 2, adding from the detail page creates a Library Entry with `WISHLIST`.
_Avoid_: Collection item, log, backlog entry

**Status**:
The consumption state of a Library Entry: `WISHLIST`, `IN_PROGRESS`, `COMPLETED`, `DROPPED`, `ON_HOLD`. For a `Movie` it is set directly; for a `TV Series` it is derived from `Season Watch` progress (`0` watched → `WISHLIST`, `some` → `IN_PROGRESS`, `all` counted seasons watched → `COMPLETED`). `DROPPED`/`ON_HOLD` remain manually set. Removal hard-deletes the entry and its `Season Watch` rows regardless of status.
_Avoid_: State, stage

**Wishlist**:
The filtered view of a User's Library Entries where `status = WISHLIST`. The UI term "Backlog" maps to this view.
_Avoid_: Backlog as separate concept, watchlist

**Watched**:
The filtered view of a User's Library Entries where `status = COMPLETED`. For a `Movie` it is always paired with a `Rating`; for a `TV Series` `Rating` lives per `Season Watch` and `Library Entry` `Rating` is optional. The UI label "Watched" maps to `COMPLETED`; "Completed" is the domain term.
_Avoid_: Watched as separate entity, history, seen

**Rating**:
An integer 1–5 representing the User's 5-star assessment. For a `Movie` `Library Entry` it is required when `status = COMPLETED` and forbidden when `WISHLIST`; for a `TV Series` it is attached per `Season Watch` (nullable 1–5) and is optional at the `Library Entry` when `Season Watch` rows exist, but remains required for a one-shot series mark without seasons. `Rating` is mutable via update and rendered as stars.
_Avoid_: Vote, score, stars as domain term (stars is presentation)

**Completed**:
The `Status` value indicating a `Library Entry` has been consumed (watched). For a `Movie` a `Completed` entry must carry a `Rating` 1–5; for a `TV Series` with `Season Watch` rows the series `Rating` is optional and per-season `Rating`s are used.
_Avoid_: Watched as status value, finished

**Catalog**:
The external source of truth for searchable works (TMDB in phase 2), queried server-side via a proxied search and detail. The system never stores Catalog credentials in the browser.
_Avoid_: Provider, API as domain term
