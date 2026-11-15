package dev.cloudpilot.postmortems;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/postmortem")
public class PostmortemController {

    private final PostmortemService postmortems;

    public PostmortemController(PostmortemService postmortems) {
        this.postmortems = postmortems;
    }

    @GetMapping
    public PostmortemView get(@PathVariable UUID incidentId) {
        return postmortems.get(incidentId);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PostmortemView generate(@PathVariable UUID incidentId, @AuthenticationPrincipal Jwt jwt) {
        return postmortems.generate(incidentId, jwt.getClaimAsString("email"));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public PostmortemView edit(@PathVariable UUID incidentId, @Valid @RequestBody EditRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return postmortems.edit(incidentId, new PostmortemContent(request.summary(), request.impact(),
                request.rootCause(), request.resolution(), request.prevention()), jwt.getClaimAsString("email"));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public PostmortemView approve(@PathVariable UUID incidentId, @AuthenticationPrincipal Jwt jwt) {
        return postmortems.approve(incidentId, jwt.getClaimAsString("email"));
    }

    public record EditRequest(@NotNull @Size(max = 5000) String summary,
                              @NotNull @Size(max = 5000) String impact,
                              @NotNull @Size(max = 5000) String rootCause,
                              @NotNull @Size(max = 5000) String resolution,
                              @NotNull @Size(max = 20) List<@Size(max = 500) String> prevention) {
    }
}
