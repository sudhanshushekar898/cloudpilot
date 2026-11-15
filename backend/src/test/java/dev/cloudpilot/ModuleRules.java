package dev.cloudpilot;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** Custom ArchUnit conditions for CloudPilot's module layout: dev.cloudpilot.&lt;module&gt;.. */
final class ModuleRules {

    private static final String ROOT = "dev.cloudpilot.";

    private ModuleRules() {
    }

    /** "dev.cloudpilot.incidents.IncidentService" -> "incidents". */
    static String moduleOf(JavaClass javaClass) {
        String name = javaClass.getPackageName();
        if (!name.startsWith(ROOT)) {
            return "";
        }
        String rest = name.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** Every class that depends on the target (calls, fields, parameters, types) is in the same module. */
    static ArchCondition<JavaClass> onlyBeAccessedFromTheirOwnModule() {
        return new ArchCondition<>("only be used by classes in their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String module = moduleOf(target);
                for (Dependency dependency : target.getDirectDependenciesToSelf()) {
                    JavaClass origin = dependency.getOriginClass();
                    if (!origin.getPackageName().startsWith(ROOT)) {
                        continue;   // framework-generated proxies and the like
                    }
                    String from = moduleOf(origin);
                    if (!from.equals(module)) {
                        events.add(SimpleConditionEvent.violated(target,
                                "%s (module '%s') uses %s (module '%s'): %s".formatted(origin.getSimpleName(), from,
                                        target.getSimpleName(), module, dependency.getDescription())));
                    }
                }
            }
        };
    }
}
