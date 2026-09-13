# Image Guess via vision model, in-memory only

Chose a separate ImageGuessService over WS-binary with a vision-capable OpenAI-compatible model and Catalog best-effort resolve, holding bytes in memory only, because the chat WS is text-only and privacy by default outweighs caching.
