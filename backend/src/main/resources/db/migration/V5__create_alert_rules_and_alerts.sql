-- A rule says: "for resources in scope, if <metric> <operator> <threshold> holds for
-- <duration_seconds>, fire an alert with <severity>". resource_type NULL = all resources.
CREATE TABLE alert_rules (
    id               UUID PRIMARY KEY,
    name             VARCHAR(100)     NOT NULL UNIQUE,
    metric           VARCHAR(20)      NOT NULL,
    operator         VARCHAR(2)       NOT NULL,
    threshold        DOUBLE PRECISION NOT NULL,
    duration_seconds INTEGER          NOT NULL,
    severity         VARCHAR(10)      NOT NULL,
    resource_type    VARCHAR(20),
    enabled          BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT alert_rules_metric_check
        CHECK (metric IN ('CPU', 'MEMORY', 'LATENCY_P95', 'ERROR_RATE', 'AVAILABILITY')),
    CONSTRAINT alert_rules_operator_check CHECK (operator IN ('GT', 'LT')),
    CONSTRAINT alert_rules_duration_check CHECK (duration_seconds BETWEEN 60 AND 3600),
    CONSTRAINT alert_rules_severity_check CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO')),
    CONSTRAINT alert_rules_resource_type_check
        CHECK (resource_type IS NULL OR resource_type IN ('EC2', 'RDS', 'API', 'QUEUE', 'POD'))
);

CREATE TABLE alerts (
    id             UUID PRIMARY KEY,
    rule_id        UUID             NOT NULL REFERENCES alert_rules (id) ON DELETE CASCADE,
    resource_id    UUID             NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    status         VARCHAR(10)      NOT NULL,
    severity       VARCHAR(10)      NOT NULL,
    message        VARCHAR(300)     NOT NULL,
    observed_value DOUBLE PRECISION NOT NULL,
    fired_at       TIMESTAMPTZ      NOT NULL,
    resolved_at    TIMESTAMPTZ,
    CONSTRAINT alerts_status_check CHECK (status IN ('FIRING', 'RESOLVED')),
    CONSTRAINT alerts_resolved_check CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

-- At most one FIRING alert per rule and resource. The engine checks this too, but the
-- database is the last line of defence against duplicates (e.g. two engine runs overlapping).
CREATE UNIQUE INDEX alerts_one_firing_per_rule_and_resource
    ON alerts (rule_id, resource_id) WHERE status = 'FIRING';

CREATE INDEX alerts_fired_at ON alerts (fired_at DESC);
