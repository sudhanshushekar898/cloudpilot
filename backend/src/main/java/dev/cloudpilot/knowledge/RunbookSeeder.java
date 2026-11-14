package dev.cloudpilot.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the runbooks shipped in src/main/resources/runbooks on startup. Idempotent: a runbook
 * that already exists (by slug) is left alone, so admins' edits survive restarts.
 */
@Component
@ConditionalOnProperty(prefix = "cloudpilot.knowledge", name = "seed-runbooks", havingValue = "true",
        matchIfMissing = true)
public class RunbookSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunbookSeeder.class);

    private final KnowledgeService knowledge;
    private final RunbookRepository runbooks;

    public RunbookSeeder(KnowledgeService knowledge, RunbookRepository runbooks) {
        this.knowledge = knowledge;
        this.runbooks = runbooks;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:runbooks/*.md");
        for (Resource file : files) {
            String markdown = file.getContentAsString(StandardCharsets.UTF_8);
            String title = titleOf(markdown, Objects.requireNonNull(file.getFilename()));
            if (runbooks.existsBySlug(KnowledgeService.slugify(title))) {
                continue;
            }
            knowledge.create(title, markdown, "system");
            log.info("Seeded runbook '{}'", title);
        }
    }

    /** The first "# " heading, or the file name if there isn't one. */
    static String titleOf(String markdown, String filename) {
        return markdown.lines()
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .findFirst()
                .orElse(filename.replaceFirst("\\.md$", "").replace('-', ' '));
    }
}
