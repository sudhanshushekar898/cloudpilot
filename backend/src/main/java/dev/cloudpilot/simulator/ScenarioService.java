package dev.cloudpilot.simulator;

import java.util.UUID;

import dev.cloudpilot.metrics.Deployment;
import dev.cloudpilot.metrics.EventStore;
import dev.cloudpilot.resources.CloudResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts scenarios and records the deployment that "caused" them, like a CI pipeline would. */
@Service
public class ScenarioService {

    private final ScenarioManager manager;
    private final CloudResourceRepository resources;
    private final EventStore events;

    public ScenarioService(ScenarioManager manager, CloudResourceRepository resources, EventStore events) {
        this.manager = manager;
        this.resources = resources;
        this.events = events;
    }

    @Transactional
    public ActiveScenario start(ScenarioType type, String startedBy) {
        ActiveScenario scenario = manager.start(type, startedBy);
        type.deployment().ifPresent(spec -> resources.findByName(spec.resourceName()).ifPresent(resource ->
                events.saveDeployment(new Deployment(UUID.randomUUID(), resource.getId(), spec.version(),
                        spec.description(), "ci-pipeline", scenario.startedAt().minus(spec.before())))));
        return scenario;
    }
}
