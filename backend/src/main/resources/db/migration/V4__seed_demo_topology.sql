-- Demo topology: a small e-commerce checkout system.
--
--   web-frontend (EC2) ──> checkout-api (API) ──> orders-db (RDS)
--                     └──> catalog-api (API)  └──> payments-queue (QUEUE) <── payments-worker (POD)
--                              └──> inventory-pod (POD)        payments-worker ──> orders-db
--
-- Fixed ids keep demos, docs and tests stable.

INSERT INTO resources (id, name, type, region, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'web-frontend',    'EC2',   'ap-south-1', 'Web servers behind the load balancer'),
    ('00000000-0000-0000-0000-000000000002', 'checkout-api',    'API',   'ap-south-1', 'Places orders and starts payment'),
    ('00000000-0000-0000-0000-000000000003', 'catalog-api',     'API',   'ap-south-1', 'Product listing and search'),
    ('00000000-0000-0000-0000-000000000004', 'orders-db',       'RDS',   'ap-south-1', 'PostgreSQL database for orders'),
    ('00000000-0000-0000-0000-000000000005', 'payments-queue',  'QUEUE', 'ap-south-1', 'SQS queue of payment jobs'),
    ('00000000-0000-0000-0000-000000000006', 'payments-worker', 'POD',   'ap-south-1', 'Kubernetes pods that process payments'),
    ('00000000-0000-0000-0000-000000000007', 'inventory-pod',   'POD',   'ap-south-1', 'Kubernetes pods that track stock levels');

INSERT INTO resource_dependencies (resource_id, depends_on_id) VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005'),
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000007'),
    ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000005'),
    ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000004');
