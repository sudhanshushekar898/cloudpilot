-- Cloud resources being monitored (simulated in v1).
CREATE TABLE resources (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    type        VARCHAR(20)  NOT NULL,
    region      VARCHAR(30)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT resources_type_check CHECK (type IN ('EC2', 'RDS', 'API', 'QUEUE', 'POD'))
);

-- "resource_id depends on depends_on_id": if the dependency fails, the resource is affected.
-- Used later to group related alerts into one incident.
CREATE TABLE resource_dependencies (
    resource_id   UUID NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    depends_on_id UUID NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    PRIMARY KEY (resource_id, depends_on_id),
    CONSTRAINT resource_dependencies_no_self CHECK (resource_id <> depends_on_id)
);

-- One row per resource, metric and sample time. The only high-volume table:
-- written in batches by the simulator, older rows removed by a retention job.
CREATE TABLE metric_samples (
    id          BIGSERIAL PRIMARY KEY,
    resource_id UUID             NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    metric      VARCHAR(20)      NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ      NOT NULL,
    CONSTRAINT metric_samples_metric_check
        CHECK (metric IN ('CPU', 'MEMORY', 'LATENCY_P95', 'ERROR_RATE', 'AVAILABILITY'))
);

-- Every read is "this resource, this metric, this time range", so index exactly that.
CREATE INDEX metric_samples_lookup ON metric_samples (resource_id, metric, recorded_at DESC);
-- Retention deletes by time only.
CREATE INDEX metric_samples_recorded_at ON metric_samples (recorded_at);
