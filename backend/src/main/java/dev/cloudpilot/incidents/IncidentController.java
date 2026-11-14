package dev.cloudpilot.incidents;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService commands;
    private final IncidentQueryService queries;

    public IncidentController(IncidentService commands, IncidentQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    private static IncidentService.Actor actor(Jwt jwt) {
        return new IncidentService.Actor(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    @GetMapping
    public List<IncidentViews.Summary> list(@RequestParam(defaultValue = "ACTIVE") String scope) {
        return queries.list(IncidentQueryService.Scope.valueOf(scope.toUpperCase()));
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of("active", queries.activeCount());
    }

    @GetMapping("/{id}")
    public IncidentViews.Detail detail(@PathVariable UUID id) {
        return queries.detail(id);
    }

    @GetMapping("/{id}/timeline")
    public List<IncidentViews.TimelineItem> timeline(@PathVariable UUID id,
                                                     @RequestParam(name = "type", required = false) Set<IncidentViews.ItemType> types,
                                                     @RequestParam(required = false) UUID resourceId) {
        return queries.timeline(id, types == null ? Set.of() : types, resourceId);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public IncidentViews.Detail acknowledge(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        commands.acknowledge(id, actor(jwt));
        return queries.detail(id);
    }

    @PostMapping("/{id}/investigate")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public IncidentViews.Detail investigate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        commands.startInvestigating(id, actor(jwt));
        return queries.detail(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public IncidentViews.Detail resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        commands.resolve(id, actor(jwt), request.resolution());
        return queries.detail(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public IncidentViews.Detail assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        commands.assign(id, actor(jwt), request.userId());
        return queries.detail(id);
    }

    /** Any signed-in user, including developers, can add context to an incident. */
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public void comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        commands.comment(id, actor(jwt), request.text());
    }

    public record ResolveRequest(@NotBlank @Size(max = 2000) String resolution) {
    }

    public record AssignRequest(@NotNull UUID userId) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String text) {
    }
}
