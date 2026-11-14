-- Every analysis is kept, with the exact evidence it was given, so an answer can always be
-- traced back to what the model saw (and compared with what really happened in the postmortem).
CREATE TABLE incident_analyses (
    id           UUID PRIMARY KEY,
    incident_id  UUID         NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    analyzer     VARCHAR(20)  NOT NULL,
    model        VARCHAR(60),
    requested_by VARCHAR(254) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    result       JSONB        NOT NULL
);

CREATE INDEX incident_analyses_latest ON incident_analyses (incident_id, created_at DESC);
