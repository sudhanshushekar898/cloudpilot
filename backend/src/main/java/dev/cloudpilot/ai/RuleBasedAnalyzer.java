package dev.cloudpilot.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic analysis that needs no API key: follow the dependency graph to the most
 * upstream resource with alerts, check for a deployment to it shortly before the first alert,
 * and take verification and remediation steps from the retrieved runbook sections.
 *
 * It is the fallback whenever the LLM is unavailable, errors or refuses, and the baseline the
 * LLM has to beat. It never interprets log text; it only cites it.
 */
public class RuleBasedAnalyzer implements Analyzer {

    /** A release this long before the first alert is still a suspect. */
    static final Duration DEPLOYMENT_SUSPECT_WINDOW = Duration.ofMinutes(60);
    private static final int MAX_STEPS = 4;
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*]|\\d+\\.)\\s+(.+)$");

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public AnalysisDraft analyze(EvidenceBundle bundle) {
        List<EvidenceItem> alerts = bundle.ofKind(EvidenceItem.Kind.ALERT);
        String origin = bundle.probableOrigin() != null ? bundle.probableOrigin()
                : alerts.stream().map(EvidenceItem::resourceName).findFirst().orElse("unknown resource");

        List<EvidenceItem> originAlerts = alerts.stream().filter(a -> origin.equals(a.resourceName())).toList();
        List<EvidenceItem> originLogs = bundle.ofKind(EvidenceItem.Kind.LOG).stream()
                .filter(l -> origin.equals(l.resourceName())).toList();
        Optional<EvidenceItem> suspectDeployment = suspectDeployment(bundle, origin, alerts);

        List<AnalysisDraft.Claim> observed = observations(bundle, origin);

        AnalysisDraft.Claim cause;
        double confidence;
        List<String> causeCitations = new ArrayList<>();
        if (suspectDeployment.isPresent()) {
            EvidenceItem deploy = suspectDeployment.get();
            causeCitations.add(deploy.id());
            originAlerts.forEach(a -> causeCitations.add(a.id()));
            originLogs.stream().limit(2).forEach(l -> causeCitations.add(l.id()));
            cause = new AnalysisDraft.Claim("A deployment to " + origin + " (" + deploy.text()
                    + ") shortly before the first alert is the most likely cause.", causeCitations);
            confidence = 0.6;
        } else {
            originAlerts.forEach(a -> causeCitations.add(a.id()));
            originLogs.stream().limit(2).forEach(l -> causeCitations.add(l.id()));
            String detail = originLogs.isEmpty() ? "" : " Its logs show: \"" + originLogs.getFirst().text() + "\".";
            cause = new AnalysisDraft.Claim(origin + " is the most likely origin: it alerted first in the dependency "
                    + "chain and depends on nothing else in this incident." + detail, causeCitations);
            confidence = originLogs.isEmpty() ? 0.35 : 0.5;
        }

        List<AnalysisDraft.Step> verification = stepsFromRunbook(bundle, "verify");
        if (verification.isEmpty()) {
            verification = List.of(new AnalysisDraft.Step("Check " + origin
                    + "'s metrics and logs from a few minutes before the first alert.",
                    originAlerts.stream().map(EvidenceItem::id).limit(1).toList()));
        }
        List<AnalysisDraft.Step> remediation = new ArrayList<>(stepsFromRunbook(bundle, "mitigation"));
        if (suspectDeployment.isPresent() && remediation.stream().noneMatch(s -> s.text().toLowerCase().contains("roll back"))) {
            remediation.addFirst(new AnalysisDraft.Step("Roll back " + origin + " to the previous version through "
                    + "the pipeline, then watch error rate for 10 minutes.", List.of(suspectDeployment.get().id())));
        }

        String summary = cause.text() + " " + (bundle.resources().size() - 1) + " other resource(s) show knock-on symptoms.";
        String reason = "Rule-based analysis: it follows the dependency graph and deployment times, and does not "
                + "interpret log text. Verify before acting.";
        return new AnalysisDraft(summary, observed, cause, confidence, reason, verification, remediation);
    }

    private static Optional<EvidenceItem> suspectDeployment(EvidenceBundle bundle, String origin, List<EvidenceItem> alerts) {
        if (alerts.isEmpty()) {
            return Optional.empty();
        }
        var firstAlert = alerts.stream().map(EvidenceItem::occurredAt).min(Comparator.naturalOrder()).orElseThrow();
        return bundle.ofKind(EvidenceItem.Kind.DEPLOYMENT).stream()
                .filter(d -> origin.equals(d.resourceName()))
                .filter(d -> !d.occurredAt().isAfter(firstAlert)
                        && !d.occurredAt().isBefore(firstAlert.minus(DEPLOYMENT_SUSPECT_WINDOW)))
                .max(Comparator.comparing(EvidenceItem::occurredAt));
    }

    private static List<AnalysisDraft.Claim> observations(EvidenceBundle bundle, String origin) {
        List<AnalysisDraft.Claim> observed = new ArrayList<>();
        for (EvidenceItem alert : bundle.ofKind(EvidenceItem.Kind.ALERT).stream().limit(5).toList()) {
            observed.add(new AnalysisDraft.Claim("Alert on " + alert.resourceName() + ": " + alert.text(), List.of(alert.id())));
        }
        for (EvidenceItem log : bundle.ofKind(EvidenceItem.Kind.LOG).stream().limit(3).toList()) {
            observed.add(new AnalysisDraft.Claim(log.resourceName() + " logged: " + log.text(), List.of(log.id())));
        }
        for (EvidenceItem deploy : bundle.ofKind(EvidenceItem.Kind.DEPLOYMENT)) {
            String note = bundle.resources().contains(deploy.resourceName())
                    ? (deploy.resourceName().equals(origin) ? "" : " It is not the probable origin.")
                    : " " + deploy.resourceName() + " is not one of the affected resources, so this release is unlikely to be the cause.";
            observed.add(new AnalysisDraft.Claim(deploy.resourceName() + " was deployed: " + deploy.text() + "." + note,
                    List.of(deploy.id())));
        }
        return observed;
    }

    /** List items from the first retrieved runbook section whose heading contains {@code keyword}. */
    private static List<AnalysisDraft.Step> stepsFromRunbook(EvidenceBundle bundle, String keyword) {
        return bundle.ofKind(EvidenceItem.Kind.RUNBOOK).stream()
                .filter(r -> r.source() != null && r.source().toLowerCase().contains(keyword))
                .findFirst()
                .map(section -> section.text().lines()
                        .map(LIST_ITEM::matcher)
                        .filter(Matcher::matches)
                        .map(m -> m.group(1).replace("**", "").replace("`", "").trim())
                        .limit(MAX_STEPS)
                        .map(text -> new AnalysisDraft.Step(text, List.of(section.id())))
                        .toList())
                .orElse(List.of());
    }
}
