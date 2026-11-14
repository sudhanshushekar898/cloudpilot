package dev.cloudpilot.auth;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountRepository users;
    private final UserDirectory directory;

    public UserController(UserAccountRepository users, UserDirectory directory) {
        this.users = users;
        this.directory = directory;
    }

    /** People an incident can be assigned to (for the assign dropdown). */
    @GetMapping("/assignable")
    @PreAuthorize("hasAnyRole('SRE', 'ADMIN')")
    public List<UserDirectory.Person> assignable() {
        return directory.assignable();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LoginResult.UserSummary> list() {
        return users.findAll(Sort.by("email")).stream()
                .map(LoginResult.UserSummary::of)
                .toList();
    }
}
