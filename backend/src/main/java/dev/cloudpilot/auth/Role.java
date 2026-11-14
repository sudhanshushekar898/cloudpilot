package dev.cloudpilot.auth;

/**
 * What a user is allowed to do. See docs/design.md section 4 for the full matrix.
 */
public enum Role {
    /** Manages users, alert rules and runbooks; can do everything an SRE can. */
    ADMIN,
    /** Owns incidents: acknowledge, assign, investigate, resolve, approve postmortems. */
    SRE,
    /** Read access to dashboards and incidents; can ask the AI and comment. */
    DEVELOPER
}
