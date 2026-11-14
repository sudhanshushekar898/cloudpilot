package dev.cloudpilot.resources;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "resources")
public class CloudResource {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String description;

    /**
     * Ids of resources this one depends on. Stored as plain ids (not entity links) so
     * loading a resource never pulls in the whole dependency graph.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resource_dependencies", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "depends_on_id")
    private Set<UUID> dependsOn = new HashSet<>();

    protected CloudResource() {
        // for JPA
    }

    public CloudResource(UUID id, String name, ResourceType type, String region, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.region = region;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResourceType getType() {
        return type;
    }

    public String getRegion() {
        return region;
    }

    public String getDescription() {
        return description;
    }

    public Set<UUID> getDependsOn() {
        return Set.copyOf(dependsOn);
    }
}
