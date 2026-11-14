package dev.cloudpilot.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker(400);

    @Test
    void splitsBySectionAndKeepsTheHeadingPath() {
        String markdown = """
                # Orders DB connection exhaustion

                Applies to orders-db (RDS PostgreSQL).

                ## Symptoms

                Errors like "too many clients already".

                ## Mitigation

                ### Short term

                Restart the noisiest client pods.

                ### Long term

                Add PgBouncer in transaction mode.
                """;

        List<MarkdownChunker.Chunk> chunks = chunker.chunk("Orders DB connection exhaustion", markdown);

        assertThat(chunks).extracting(MarkdownChunker.Chunk::heading).containsExactly(
                "Orders DB connection exhaustion",
                "Orders DB connection exhaustion > Symptoms",
                "Orders DB connection exhaustion > Mitigation > Short term",
                "Orders DB connection exhaustion > Mitigation > Long term");
        assertThat(chunks.get(1).content()).isEqualTo("Errors like \"too many clients already\".");
        assertThat(chunks).extracting(MarkdownChunker.Chunk::ordinal).containsExactly(0, 1, 2, 3);
    }

    @Test
    void textBeforeTheFirstHeadingUsesTheTitle() {
        List<MarkdownChunker.Chunk> chunks = chunker.chunk("Queue backlog", "Some intro.\n\n## Checks\n\nLook at lag.");

        assertThat(chunks.getFirst()).isEqualTo(new MarkdownChunker.Chunk(0, "Queue backlog", "Some intro."));
        assertThat(chunks.get(1).heading()).isEqualTo("Queue backlog > Checks");
    }

    @Test
    void ignoresHeadingLikeLinesInsideCodeBlocks() {
        String markdown = """
                ## Commands

                ```bash
                # this is a shell comment, not a heading
                kubectl rollout undo deployment/checkout-api
                ```
                """;

        List<MarkdownChunker.Chunk> chunks = chunker.chunk("Rollback", markdown);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.heading()).isEqualTo("Rollback > Commands");
            assertThat(chunk.content()).contains("# this is a shell comment").contains("kubectl rollout undo");
        });
    }

    @Test
    void longSectionsAreSplitOnParagraphBoundaries() {
        String paragraph = "word ".repeat(50).trim();   // ~250 characters
        String markdown = "## Steps\n\n" + paragraph + "\n\n" + paragraph + "\n\n" + paragraph;

        List<MarkdownChunker.Chunk> chunks = chunker.chunk("Big runbook", markdown);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(MarkdownChunker.Chunk::heading).containsExactly(
                "Big runbook > Steps", "Big runbook > Steps (part 2)", "Big runbook > Steps (part 3)");
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(400));
    }

    @Test
    void skipsSectionsWithNoText() {
        List<MarkdownChunker.Chunk> chunks = chunker.chunk("T", "## Empty\n\n## Real\n\nText.");

        assertThat(chunks).extracting(MarkdownChunker.Chunk::heading).containsExactly("T > Real");
    }
}
