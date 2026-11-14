package dev.cloudpilot.incidents;

import java.time.Instant;
import java.util.UUID;

import dev.cloudpilot.alerts.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-08: OPEN -> ACKNOWLEDGED -> INVESTIGATING -> RESOLVED, nothing else. */
class IncidentTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID SRE = UUID.randomUUID();

    private Incident incident;

    @BeforeEach
    void setUp() {
        incident = new Incident("checkout-api error rate 18% > 5% for 5 min", Severity.WARNING, T0);
    }

    @Test
    void newIncidentIsOpenAndUnassigned() {
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getAssigneeId()).isNull();
        assertThat(incident.getOpenedAt()).isEqualTo(T0);
        assertThat(incident.getLastAlertAt()).isEqualTo(T0);
    }

    @Test
    void followsTheFullLifecycle() {
        incident.acknowledge(SRE, T0.plusSeconds(60));
        incident.startInvestigating(T0.plusSeconds(120));
        incident.resolve("Rolled back checkout-api to v2.13.4", T0.plusSeconds(900));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getAcknowledgedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(incident.getInvestigatingAt()).isEqualTo(T0.plusSeconds(120));
        assertThat(incident.getResolvedAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(incident.getResolution()).isEqualTo("Rolled back checkout-api to v2.13.4");
    }

    @Test
    void acknowledgingAssignsTheIncidentIfNobodyOwnsIt() {
        incident.acknowledge(SRE, T0.plusSeconds(60));

        assertThat(incident.getAssigneeId()).isEqualTo(SRE);
    }

    @Test
    void acknowledgingKeepsAnExistingAssignee() {
        UUID owner = UUID.randomUUID();
        incident.assign(owner);

        incident.acknowledge(SRE, T0.plusSeconds(60));

        assertThat(incident.getAssigneeId()).isEqualTo(owner);
    }

    @Test
    void cannotSkipSteps() {
        assertThatThrownBy(() -> incident.startInvestigating(T0))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessage("Cannot move incident from OPEN to INVESTIGATING");
        assertThatThrownBy(() -> incident.resolve("fixed", T0))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void cannotGoBackwardsOrRepeat() {
        incident.acknowledge(SRE, T0);

        assertThatThrownBy(() -> incident.acknowledge(SRE, T0)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void resolvedIncidentsAreFinal() {
        incident.acknowledge(SRE, T0);
        incident.startInvestigating(T0);
        incident.resolve("done", T0);

        assertThatThrownBy(() -> incident.assign(SRE)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> incident.recordAlert(Severity.CRITICAL, T0)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void resolvingRequiresAResolutionNote() {
        incident.acknowledge(SRE, T0);
        incident.startInvestigating(T0);

        assertThatThrownBy(() -> incident.resolve("  ", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void severityOnlyEscalates() {
        incident.recordAlert(Severity.CRITICAL, T0.plusSeconds(30));
        incident.recordAlert(Severity.INFO, T0.plusSeconds(60));

        assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(incident.getLastAlertAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void displayNumberUsesTheIncPrefix() {
        assertThat(Incident.displayNumber(42)).isEqualTo("INC-42");
    }
}
