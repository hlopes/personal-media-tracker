-- V8: Enable pgvector extension for RAG embeddings (quarkus-langchain4j-pgvector)
-- Required before pgvector store creates its table; image must contain vector.control
-- (pgvector/pgvector:pg16). Keep idempotent for DevServices and docker-compose.
CREATE EXTENSION IF NOT EXISTS vector;
