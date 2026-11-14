package dev.cloudpilot.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountRepository users;

    private DemoUserSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoUserSeeder(users, passwordEncoder, new DemoProperties(true, "Demo-pass-1"), clock);
    }

    @Test
    void createsOneUserPerRoleWhenNoneExist() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);

        seeder.run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users, times(3)).save(saved.capture());
        List<UserAccount> created = saved.getAllValues();
        assertThat(created).extracting(UserAccount::getRole)
                .containsExactlyInAnyOrder(Role.ADMIN, Role.SRE, Role.DEVELOPER);
        assertThat(created).allSatisfy(user ->
                assertThat(passwordEncoder.matches("Demo-pass-1", user.getPasswordHash())).isTrue());
    }

    @Test
    void skipsUsersThatAlreadyExist() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(true);

        seeder.run(null);

        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenSeedingIsDisabled() {
        DemoUserSeeder disabled = new DemoUserSeeder(users, passwordEncoder, new DemoProperties(false, "x"), clock);

        disabled.run(null);

        verify(users, never()).existsByEmailIgnoreCase(any());
        verify(users, never()).save(any());
    }
}
