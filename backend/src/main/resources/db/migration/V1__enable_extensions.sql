-- pgvector is needed later for runbook embeddings (M6).
-- Enabling it in the first migration proves the database image supports it.
CREATE EXTENSION IF NOT EXISTS vector;
