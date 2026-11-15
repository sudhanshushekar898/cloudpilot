package dev.cloudpilot.features;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureFlagController {

    private final FeatureFlags flags;

    public FeatureFlagController(FeatureFlags flags) {
        this.flags = flags;
    }

    @GetMapping
    public List<FeatureFlags.FlagView> list() {
        return flags.list();
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeatureFlags.FlagView set(@PathVariable String name, @Valid @RequestBody SetFlag request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return flags.set(name, request.enabled(), jwt.getClaimAsString("email"));
    }

    public record SetFlag(@NotNull Boolean enabled) {
    }
}
