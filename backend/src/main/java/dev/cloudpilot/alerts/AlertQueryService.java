package dev.cloudpilot.alerts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.cloudpilot.resources.CloudResource;
import dev.cloudpilot.resources.CloudResourceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for alerts: adds resource and rule names so the UI doesn't need extra calls. */
@Service
@Transactional(readOnly = true)
public class AlertQueryService {

    private final AlertRepository alerts;
    private final AlertRuleRepository rules;
    private final CloudResourceRepository resources;

    public AlertQueryService(AlertRepository alerts, AlertRuleRepository rules, CloudResourceRepository resources) {
        this.alerts = alerts;
        this.rules = rules;
        this.resources = resources;
    }

    public List<AlertView> list(Alert.Status status, int limit) {
        List<Alert> found = status == null
                ? alerts.findAllByOrderByFiredAtDesc(Limit.of(limit))
                : alerts.findByStatusOrderByFiredAtDesc(status, Limit.of(limit));

        // Both tables are small (dozens of rows), so two lookups beat a join per alert.
        Map<UUID, String> resourceNames = resources.findAll().stream()
                .collect(Collectors.toMap(CloudResource::getId, CloudResource::getName));
        Map<UUID, String> ruleNames = rules.findAll().stream()
                .collect(Collectors.toMap(AlertRule::getId, AlertRule::getName));

        return found.stream().map(alert -> new AlertView(
                alert.getId(), alert.getRuleId(), ruleNames.getOrDefault(alert.getRuleId(), "(deleted rule)"),
                alert.getResourceId(), resourceNames.getOrDefault(alert.getResourceId(), "(deleted resource)"),
                alert.getStatus(), alert.getSeverity(), alert.getMessage(), alert.getObservedValue(),
                alert.getFiredAt(), alert.getResolvedAt())).toList();
    }

    public Summary summary() {
        List<Alert> firing = alerts.findByStatusOrderByFiredAtDesc(Alert.Status.FIRING, Limit.unlimited());
        Map<Severity, Long> bySeverity = firing.stream()
                .collect(Collectors.groupingBy(Alert::getSeverity, Collectors.counting()));
        return new Summary(firing.size(), bySeverity.getOrDefault(Severity.CRITICAL, 0L),
                bySeverity.getOrDefault(Severity.WARNING, 0L));
    }

    public record AlertView(UUID id, UUID ruleId, String ruleName, UUID resourceId, String resourceName,
                            Alert.Status status, Severity severity, String message, double observedValue,
                            Instant firedAt, Instant resolvedAt) {
    }

    public record Summary(long firing, long critical, long warning) {
    }
}
