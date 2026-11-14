package dev.cloudpilot.incidents;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, UUID> {

    List<TimelineEntry> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
