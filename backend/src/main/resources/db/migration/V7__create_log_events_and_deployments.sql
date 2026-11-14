-- Application log lines worth keeping (warnings and errors), per resource.
CREATE TABLE log_events (
    id          BIGSERIAL PRIMARY KEY,
    resource_id UUID         NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    level       VARCHAR(5)   NOT NULL,
    message     VARCHAR(500) NOT NULL,
    logged_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT log_events_level_check CHECK (level IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX log_events_lookup ON log_events (resource_id, logged_at DESC);

-- Releases. "What changed just before it broke?" is the first question in most incidents.
CREATE TABLE deployments (
    id          UUID PRIMARY KEY,
    resource_id UUID         NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    version     VARCHAR(50)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    deployed_by VARCHAR(100) NOT NULL,
    deployed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX deployments_lookup ON deployments (resource_id, deployed_at DESC);
