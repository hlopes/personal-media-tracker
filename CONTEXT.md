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

**MediaItem** _(stub - phase 2)_:
A single trackable work — a Movie, TV Show, or Video Game — as catalogued by the system.
_Avoid_: Media, entry, title (overloaded)

**Library Entry** _(stub - phase 2)_:
The association between a User and a MediaItem, holding the User-specific state (status, rating, progress, notes).
_Avoid_: Collection item, log, watchlist entry

**Status** _(stub - phase 2)_:
The consumption state of a Library Entry: `WISHLIST`, `IN_PROGRESS`, `COMPLETED`, `DROPPED`, `ON_HOLD`.
_Avoid_: State, stage
