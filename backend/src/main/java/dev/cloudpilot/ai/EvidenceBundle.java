package dev.cloudpilot.ai;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything the analysis is allowed to know about an incident, and nothing more
 * (ADR-0002: evidence-only input).
 *
 * @param probableOrigin the resource the dependency graph points at, or null
 * @param dependsOn      "a depends on b" edges between the involved resources, for reasoning about ripples
 */
public record EvidenceBundle(UUID incidentId, String incidentKey, String title, String status, String question,
                             List<String> resources, String probableOrigin, List<String> dependsOn,
                             List<EvidenceItem> items) {

    public Set<String> ids() {
        return items.stream().map(EvidenceItem::id).collect(Collectors.toSet());
    }

    public List<EvidenceItem> ofKind(EvidenceItem.Kind kind) {
        return items.stream().filter(i -> i.kind() == kind).toList();
    }

    public Optional<EvidenceItem> find(String id) {
        return items.stream().filter(i -> i.id().equals(id)).findFirst();
    }
}
