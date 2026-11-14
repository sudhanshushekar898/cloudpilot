-- Default rules a new team would start with. Admins can edit or disable them in the app.
INSERT INTO alert_rules (id, name, metric, operator, threshold, duration_seconds, severity, resource_type) VALUES
    ('10000000-0000-0000-0000-000000000001', 'API error rate above 5%',       'ERROR_RATE',   'GT', 5,    300, 'CRITICAL', 'API'),
    ('10000000-0000-0000-0000-000000000002', 'Availability below 99%',        'AVAILABILITY', 'LT', 99,   120, 'CRITICAL', NULL),
    ('10000000-0000-0000-0000-000000000003', 'p95 latency above 1 s',         'LATENCY_P95',  'GT', 1000, 180, 'WARNING',  'API'),
    ('10000000-0000-0000-0000-000000000004', 'Database CPU above 90%',        'CPU',          'GT', 90,   180, 'CRITICAL', 'RDS'),
    ('10000000-0000-0000-0000-000000000005', 'Database errors above 5%',      'ERROR_RATE',   'GT', 5,    180, 'CRITICAL', 'RDS'),
    ('10000000-0000-0000-0000-000000000006', 'Queue message age above 5 s',   'LATENCY_P95',  'GT', 5000, 180, 'WARNING',  'QUEUE'),
    ('10000000-0000-0000-0000-000000000007', 'Pod memory above 90%',          'MEMORY',       'GT', 90,   180, 'WARNING',  'POD'),
    ('10000000-0000-0000-0000-000000000008', 'Pod CPU above 90%',             'CPU',          'GT', 90,   300, 'WARNING',  'POD'),
    ('10000000-0000-0000-0000-000000000009', 'Pod error rate above 5%',       'ERROR_RATE',   'GT', 5,    180, 'WARNING',  'POD'),
    ('10000000-0000-0000-0000-000000000010', 'Frontend error rate above 5%',  'ERROR_RATE',   'GT', 5,    180, 'WARNING',  'EC2');
