-- Team runbooks (Markdown). The source of truth the AI must cite, never invent.
CREATE TABLE runbooks (
    id         UUID PRIMARY KEY,
    slug       VARCHAR(120)  NOT NULL UNIQUE,
    title      VARCHAR(200)  NOT NULL,
    content    TEXT          NOT NULL,
    created_by VARCHAR(254)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);

-- Runbooks split into sections, so retrieval returns the relevant part, not a whole document.
CREATE TABLE runbook_chunks (
    id         UUID PRIMARY KEY,
    runbook_id UUID          NOT NULL REFERENCES runbooks (id) ON DELETE CASCADE,
    ordinal    INTEGER       NOT NULL,
    heading    VARCHAR(500)  NOT NULL,
    content    TEXT          NOT NULL,
    -- Kept up to date by PostgreSQL itself. Heading words weigh more (A) than body words (B).
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', heading), 'A') || setweight(to_tsvector('english', content), 'B')
    ) STORED,
    UNIQUE (runbook_id, ordinal)
);

CREATE INDEX runbook_chunks_search ON runbook_chunks USING GIN (search_vector);
