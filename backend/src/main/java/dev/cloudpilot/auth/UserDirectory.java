package dev.cloudpilot.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The auth module's public face for other modules: look up who someone is without
 * reaching into auth's repository or entities (ADR-0001 module boundaries).
 */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

    private final UserAccountRepository users;

    public UserDirectory(UserAccountRepository users) {
        this.users = users;
    }

    public record Person(UUID id, String displayName, String email, Role role) {
    }

    public Optional<Person> find(UUID id) {
        return users.findById(id).map(UserDirectory::toPerson);
    }

    public Map<UUID, Person> findAll(Collection<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(UserAccount::getId, UserDirectory::toPerson));
    }

    /** People who can own an incident: enabled SREs and admins. */
    public List<Person> assignable() {
        return users.findAll(Sort.by("displayName")).stream()
                .filter(UserAccount::isEnabled)
                .filter(u -> u.getRole() == Role.SRE || u.getRole() == Role.ADMIN)
                .map(UserDirectory::toPerson)
                .toList();
    }

    private static Person toPerson(UserAccount user) {
        return new Person(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
    }
}
