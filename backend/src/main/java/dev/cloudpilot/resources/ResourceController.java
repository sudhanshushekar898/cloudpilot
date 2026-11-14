package dev.cloudpilot.resources;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResourceView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ResourceView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/metrics")
    public MetricHistory metrics(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int minutes) {
        return service.history(id, minutes);
    }
}
