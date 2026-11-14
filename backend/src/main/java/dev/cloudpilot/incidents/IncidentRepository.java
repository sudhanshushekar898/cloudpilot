package dev.cloudpilot.incidents;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByStatusInOrderByOpenedAtDesc(Collection<IncidentStatus> statuses);

    List<Incident> findAllByOrderByOpenedAtDesc();

    long countByStatusIn(Collection<IncidentStatus> statuses);
}
