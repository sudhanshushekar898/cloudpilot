package dev.cloudpilot.ai;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any signed-in user can ask, Developers included: analysis only reads evidence and
 * recommends; it can't change anything (ADR-0002).
 */
@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/analysis")
public class AnalysisController {

    private final AnalysisService analyses;

    public AnalysisController(AnalysisService analyses) {
        this.analyses = analyses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResult analyze(@PathVariable UUID incidentId, @Valid @RequestBody AnalysisRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return analyses.analyze(incidentId, request.question(), jwt.getClaimAsString("email"));
    }

    @GetMapping
    public AnalysisResult latest(@PathVariable UUID incidentId) {
        return analyses.latest(incidentId);
    }

    public record AnalysisRequest(@Size(max = 500) String question) {
    }
}
