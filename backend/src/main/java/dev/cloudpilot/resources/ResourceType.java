package dev.cloudpilot.resources;

public enum ResourceType {
    /** Virtual machine. */
    EC2,
    /** Managed relational database. */
    RDS,
    /** HTTP service behind a load balancer or API gateway. */
    API,
    /** Message queue (SQS-style). */
    QUEUE,
    /** Kubernetes workload. */
    POD
}
