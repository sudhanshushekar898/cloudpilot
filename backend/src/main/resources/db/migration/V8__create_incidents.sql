-- An incident is one underlying problem, possibly made of many alerts.
CREATE TABLE incidents (
    id               UUID PRIMARY KEY,
    number           BIGSERIAL    NOT NULL UNIQUE,   -- human-friendly id: INC-42
    title            VARCHAR(300) NOT NULL,
    status           VARCHAR(15)  NOT NULL,
    severity         VARCHAR(10)  NOT NULL,
    assignee_id      UUID REFERENCES users (id),
    opened_at        TIMESTAMPTZ  NOT NULL,
    acknowledged_at  TIMESTAMPTZ,
    investigating_at TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    last_alert_at    TIMESTAMPTZ  NOT NULL,
    resolution       VARCHAR(2000),
    version          INTEGER      NOT NULL DEFAULT 0,  -- optimistic locking
    CONSTRAINT incidents_status_check CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED')),
    CONSTRAINT incidents_severity_check CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO'))
);

CREATE INDEX incidents_status ON incidents (status, opened_at DESC);

-- Which alerts belong to which incident, and why they were grouped.
-- Owned by the incidents module, so the alerts table never needs to know incidents exist.
CREATE TABLE incident_alerts (
    incident_id UUID         NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    alert_id    UUID         NOT NULL UNIQUE,
    resource_id UUID         NOT NULL REFERENCES resources (id),
    severity    VARCHAR(10)  NOT NULL,
    message     VARCHAR(300) NOT NULL,
    reason      VARCHAR(300) NOT NULL,
    fired_at    TIMESTAMPTZ  NOT NULL,
    resolved_at TIMESTAMPTZ,
    PRIMARY KEY (incident_id, alert_id)
);

-- Everything that happened during the incident, in order: alerts, status changes, comments.
CREATE TABLE timeline_entries (
    id          UUID PRIMARY KEY,
    incident_id UUID          NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    type        VARCHAR(20)   NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL,
    actor       VARCHAR(254),
    resource_id UUID REFERENCES resources (id),
    message     VARCHAR(2000) NOT NULL,
    CONSTRAINT timeline_entries_type_check
        CHECK (type IN ('ALERT_FIRED', 'ALERT_RESOLVED', 'STATUS_CHANGED', 'ASSIGNED', 'COMMENT'))
);

CREATE INDEX timeline_entries_incident ON timeline_entries (incident_id, occurred_at);
