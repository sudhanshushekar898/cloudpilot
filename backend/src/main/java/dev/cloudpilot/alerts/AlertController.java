package dev.cloudpilot.alerts;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertQueryService service;

    public AlertController(AlertQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertQueryService.AlertView> list(
            @RequestParam(required = false) Alert.Status status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.list(status, limit);
    }

    @GetMapping("/summary")
    public AlertQueryService.Summary summary() {
        return service.summary();
    }
}
