-- One postmortem per incident. Drafted by the system (AI or template), edited and approved by a person.
CREATE TABLE postmortems (
    id           UUID PRIMARY KEY,
    incident_id  UUID          NOT NULL UNIQUE REFERENCES incidents (id) ON DELETE CASCADE,
    status       VARCHAR(10)   NOT NULL,
    summary      TEXT          NOT NULL,
    impact       TEXT          NOT NULL,
    root_cause   TEXT          NOT NULL,
    resolution   TEXT          NOT NULL,
    -- One prevention action per line.
    prevention   TEXT          NOT NULL,
    drafted_by   VARCHAR(20)   NOT NULL,   -- "claude" or "template"
    model        VARCHAR(60),
    created_by   VARCHAR(254)  NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_by   VARCHAR(254)  NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    approved_by  VARCHAR(254),
    approved_at  TIMESTAMPTZ,
    version      INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT postmortems_status_check CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT postmortems_approval_check
        CHECK ((status = 'APPROVED') = (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
