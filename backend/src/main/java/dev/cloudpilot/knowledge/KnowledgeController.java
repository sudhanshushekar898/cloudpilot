package dev.cloudpilot.knowledge;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runbooks")
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping
    public List<KnowledgeService.RunbookSummary> list() {
        return knowledge.list();
    }

    @GetMapping("/search")
    public List<RunbookSearch.Hit> search(@RequestParam @Size(max = 500) String q,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(25) int limit) {
        return knowledge.search(q, limit);
    }

    @GetMapping("/suggestions")
    public List<RunbookSearch.Hit> suggestions(@RequestParam UUID incidentId,
                                               @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit) {
        return knowledge.suggestionsFor(incidentId, limit);
    }

    @GetMapping("/{slug}")
    public KnowledgeService.RunbookDetail get(@PathVariable String slug) {
        return knowledge.get(slug);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeService.RunbookSummary create(@Valid @RequestBody RunbookRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return knowledge.create(request.title(), request.content(), jwt.getClaimAsString("email"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String slug) {
        knowledge.delete(slug);
    }

    /** Markdown up to 200 KB: plenty for a runbook, small enough to keep the database lean. */
    public record RunbookRequest(@NotBlank @Size(max = 200) String title,
                                 @NotBlank @Size(max = 200_000) String content) {
    }
}
