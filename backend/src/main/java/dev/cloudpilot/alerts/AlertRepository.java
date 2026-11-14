package dev.cloudpilot.alerts;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByRuleIdAndStatus(UUID ruleId, Alert.Status status);

    List<Alert> findByStatusOrderByFiredAtDesc(Alert.Status status, Limit limit);

    List<Alert> findAllByOrderByFiredAtDesc(Limit limit);

    long countByStatus(Alert.Status status);
}
