-- Runtime switches an admin can flip without a redeploy (kill switches).
CREATE TABLE feature_flags (
    name        VARCHAR(50)  PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL,
    description VARCHAR(300) NOT NULL,
    updated_by  VARCHAR(254) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

INSERT INTO feature_flags (name, enabled, description, updated_by, updated_at) VALUES
    ('claude-ai', TRUE,
     'Use Claude for incident analysis and postmortem drafts. Off: rule-based analysis and template drafts.',
     'system', now()),
    ('failure-scenarios', TRUE,
     'Allow admins to start simulated failure scenarios.',
     'system', now());
