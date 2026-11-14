package dev.cloudpilot.alerts;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findAllByOrderByNameAsc();

    List<AlertRule> findByEnabledTrue();

    boolean existsByNameIgnoreCase(String name);
}
