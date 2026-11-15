package dev.cloudpilot.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.cloudpilot.alerts.Severity;
import dev.cloudpilot.incidents.IncidentStatus;
import dev.cloudpilot.incidents.IncidentViews;
import dev.cloudpilot.knowledge.RunbookSearch;
import dev.cloudpilot.resources.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceBundleBuilderTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID DB = UUID.randomUUID();
    private static final UUID API = UUID.randomUUID();
    private static final UUID CATALOG = UUID.randomUUID();

    private final EvidenceBundleBuilder builder = new EvidenceBundleBuilder(3, 2);

    private static IncidentViews.Detail incident() {
        return new IncidentViews.Detail(UUID.randomUUID(), "INC-7", "orders-db cpu 97% > 90% for 3 min",
                IncidentStatus.OPEN, Severity.CRITICAL, null, T0, null, null, null, null, false,
                List.of(
                        new IncidentViews.AlertItem(UUID.randomUUID(), DB, "orders-db", Severity.CRITICAL,
                                "orders-db cpu 97% > 90% for 3 min", "First alert", T0, null),
                        new IncidentViews.AlertItem(UUID.randomUUID(), API, "checkout-api", Severity.CRITICAL,
                                "checkout-api error rate 18% > 5% for 5 min",
                                "checkout-api depends on orders-db, already in INC-7", T0.plusSeconds(60),
                                T0.plusSeconds(900))),
                List.of(new IncidentViews.ResourceItem(DB, "orders-db", ResourceType.RDS, true),
                        new IncidentViews.ResourceItem(API, "checkout-api", ResourceType.API, false)));
    }

    private static IncidentViews.TimelineItem log(UUID resource, String name, String message, int secondsAfter) {
        return new IncidentViews.TimelineItem(IncidentViews.ItemType.LOG, T0.plusSeconds(secondsAfter), message, null,
                resource, name, "ERROR");
    }

    private static IncidentViews.TimelineItem deploy(String message, int secondsAfter) {
        return new IncidentViews.TimelineItem(IncidentViews.ItemType.DEPLOYMENT, T0.plusSeconds(secondsAfter), message,
                "ci-pipeline", CATALOG, "catalog-api", null);
    }

    private static RunbookSearch.Hit hit(String heading, String content) {
        return new RunbookSearch.Hit(UUID.randomUUID(), UUID.randomUUID(), "slug", "Runbook", heading, content,
                "snippet", 0.5);
    }

    @Test
    void numbersAlertsInTheOrderTheyFired() {
        EvidenceBundle bundle = builder.build(incident(), List.of(), List.of(), List.of(), "Why?");

        assertThat(bundle.ofKind(EvidenceItem.Kind.ALERT)).extracting(EvidenceItem::id, EvidenceItem::resourceName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("A1", "orders-db"),
                        org.assertj.core.groups.Tuple.tuple("A2", "checkout-api"));
        assertThat(bundle.find("A2")).hasValueSatisfying(a -> {
            assertThat(a.text()).isEqualTo("checkout-api error rate 18% > 5% for 5 min (since resolved)");
            assertThat(a.source()).isEqualTo("checkout-api depends on orders-db, already in INC-7");
        });
    }

    @Test
    void carriesIncidentContextAndTheProbableOrigin() {
        EvidenceBundle bundle = builder.build(incident(), List.of(), List.of(), List.of("checkout-api -> orders-db"),
                "Why did checkout fail?");

        assertThat(bundle.incidentKey()).isEqualTo("INC-7");
        assertThat(bundle.probableOrigin()).isEqualTo("orders-db");
        assertThat(bundle.resources()).containsExactly("orders-db", "checkout-api");
        assertThat(bundle.dependsOn()).containsExactly("checkout-api -> orders-db");
        assertThat(bundle.question()).isEqualTo("Why did checkout fail?");
    }

    @Test
    void collapsesRepeatedLogLinesAndKeepsTheEarliestDistinctOnes() {
        List<IncidentViews.TimelineItem> timeline = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            timeline.add(log(DB, "orders-db", "too many clients already", 10 + i));
        }
        timeline.add(log(API, "checkout-api", "HikariPool-1 - Connection is not available", 20));
        timeline.add(log(API, "checkout-api", "POST /api/orders failed: 503", 30));
        timeline.add(log(API, "checkout-api", "a fourth distinct message", 40));

        EvidenceBundle bundle = builder.build(incident(), timeline, List.of(), List.of(), "Why?");

        List<EvidenceItem> logs = bundle.ofKind(EvidenceItem.Kind.LOG);
        assertThat(logs).extracting(EvidenceItem::text).containsExactly(
                "too many clients already (seen 5 times)",
                "HikariPool-1 - Connection is not available",
                "POST /api/orders failed: 503");
        assertThat(logs.getFirst().id()).isEqualTo("L1");
        assertThat(logs.getFirst().occurredAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void includesDeploymentsAndTopRunbookSections() {
        EvidenceBundle bundle = builder.build(incident(),
                List.of(deploy("Deployed v3.2.1: Update product image CDN host", -240)),
                List.of(hit("Orders DB > Verify", "Check active connections."), hit("Orders DB > Safe mitigation", "Restart one pod."),
                        hit("Ignored third", "beyond the limit")),
                List.of(), "Why?");

        assertThat(bundle.ofKind(EvidenceItem.Kind.DEPLOYMENT)).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo("D1");
            assertThat(d.resourceName()).isEqualTo("catalog-api");
            assertThat(d.occurredAt()).isEqualTo(T0.minusSeconds(240));
        });
        assertThat(bundle.ofKind(EvidenceItem.Kind.RUNBOOK)).extracting(EvidenceItem::id, EvidenceItem::source)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("R1", "Orders DB > Verify"),
                        org.assertj.core.groups.Tuple.tuple("R2", "Orders DB > Safe mitigation"));
    }
}
