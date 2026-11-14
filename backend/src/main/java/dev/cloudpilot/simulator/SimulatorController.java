package dev.cloudpilot.simulator;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final ScenarioManager scenarios;
    private final ScenarioService scenarioService;

    public SimulatorController(ScenarioManager scenarios, ScenarioService scenarioService) {
        this.scenarios = scenarios;
        this.scenarioService = scenarioService;
    }

    @GetMapping
    public SimulatorState state() {
        List<ScenarioInfo> catalogue = Arrays.stream(ScenarioType.values()).map(ScenarioInfo::of).toList();
        return new SimulatorState(catalogue, scenarios.active().orElse(null));
    }

    @PostMapping("/scenarios/{type}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ActiveScenario start(@PathVariable ScenarioType type, @AuthenticationPrincipal Jwt jwt) {
        return scenarioService.start(type, jwt.getClaimAsString("email"));
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop() {
        scenarios.stop();
    }

    public record SimulatorState(List<ScenarioInfo> scenarios, ActiveScenario active) {
    }

    public record ScenarioInfo(ScenarioType type, String title, String description, long durationMinutes,
                               List<String> affectedResources) {

        static ScenarioInfo of(ScenarioType type) {
            return new ScenarioInfo(type, type.title(), type.description(), type.duration().toMinutes(),
                    type.affectedResources());
        }
    }
}
