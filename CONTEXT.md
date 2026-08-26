# Personal Media Tracker

A personal system for tracking consumption of movies, TV shows and video games across a full lifecycle (wishlist → in progress → completed).

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

**Library Entry**:
The association between a User and a MediaItem, holding the User-specific lifecycle state. In phase 2, adding from the detail page creates a Library Entry with `WISHLIST`.
_Avoid_: Collection item, log, backlog entry

**Status**:
The consumption state of a Library Entry: `WISHLIST`, `IN_PROGRESS`, `COMPLETED`, `DROPPED`, `ON_HOLD`. Phase 2 uses `WISHLIST` only; phase 3 uses `COMPLETED` for watched items. Removal hard-deletes the entry regardless of status.
_Avoid_: State, stage

**Wishlist**:
The filtered view of a User's Library Entries where `status = WISHLIST`. The UI term "Backlog" maps to this view.
_Avoid_: Backlog as separate concept, watchlist

**Watched**:
The filtered view of a User's Library Entries where `status = COMPLETED`, always paired with a `Rating`. The UI label "Watched" maps to `COMPLETED`; "Completed" is the domain term.
_Avoid_: Watched as separate entity, history, seen

**Rating**:
An integer 1–5 attached to a `Library Entry` with `status = COMPLETED`, representing the User's 5-star assessment of the `MediaItem`. `Rating` is required when `COMPLETED`, forbidden when `WISHLIST`, and mutable via update.
_Avoid_: Vote, score, stars as domain term (stars is presentation)

**Completed**:
The `Status` value indicating a `Library Entry` has been consumed (watched). A `Completed` entry must carry a `Rating` 1–5.
_Avoid_: Watched as status value, finished

**Catalog**:
The external source of truth for searchable works (TMDB in phase 2), queried server-side via a proxied search and detail. The system never stores Catalog credentials in the browser.
_Avoid_: Provider, API as domain term
