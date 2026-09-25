# The Assistant acts on the Library via LLM tools: additive-only, JWT identity, search-then-add

We let the Assistant add a MediaItem to the User's Watchlist when explicitly asked, by exposing LangChain4j tools to the model instead of parsing intent server-side. The scope is deliberately additive-only: no completing, rating, status changes or removal, because a small local model (Gemma 4 E2B Q4 on llama.cpp) can misread intent and destructive actions are not reversible. The tools take no User argument; the User is always resolved from the WebSocket's JWT, so a prompt cannot steer writes into another User's Library. Resolution is two-step: `searchCatalog` returns Catalog candidates (externalId, title, year, MediaType) and `addToWatchlist(externalId, mediaType)` adds an exact item, ensuring the MediaItem is cached first. The model adds immediately when the match is unambiguous and asks the User when several candidates fit. Tools never throw; they return explicit results (`ADDED`, `ALREADY_IN_LIBRARY:<status>`, `NOT_FOUND`, failure reasons) and the Assistant must not claim success without an `ADDED` result. An existing Library Entry is reported with its real Status and never changed.

## Considered Options

- **Single tool taking a title** (server picks the best Catalog match): rejected, silently picks the wrong work for remakes and same-named titles.
- **Page context from the browser** (current detail page's item): rejected, doesn't cover items that came up only in conversation.
- **Server-side intent parsing as a fallback for weak tool calling**: rejected; if the model proves unreliable we swap to a larger model via configuration (`chat-model.model-name`) rather than maintain a parallel NLU path.

## Consequences

- Each add costs three model turns (search, add, final reply); with Gemma's reasoning enabled this is ~35–45 s with no streamed output until the final turn. Accepted for v1; progress events over the WebSocket are a possible follow-up.
- Chat memory is per WebSocket connection; the UI "Clear" button does not reset it, so "add it" after Clear may still refer to the earlier item. Tracked as a follow-up.

