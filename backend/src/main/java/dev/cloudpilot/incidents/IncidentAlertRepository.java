package dev.cloudpilot.incidents;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAlertRepository extends JpaRepository<IncidentAlert, IncidentAlert.Key> {

    List<IncidentAlert> findByIncidentIdOrderByFiredAtAsc(UUID incidentId);

    List<IncidentAlert> findByIncidentIdIn(Collection<UUID> incidentIds);

    Optional<IncidentAlert> findByAlertId(UUID alertId);
}
