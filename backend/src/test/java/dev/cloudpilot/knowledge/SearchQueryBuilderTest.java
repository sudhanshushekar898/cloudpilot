package dev.cloudpilot.knowledge;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryBuilderTest {

    private final SearchQueryBuilder builder = new SearchQueryBuilder(10);

    @Test
    void joinsDistinctWordsWithOr() {
        assertThat(builder.build("connection pool exhausted, connection refused"))
                .contains("connection | pool | exhausted | refused");
    }

    @Test
    void dropsNumbersUnitsAndShortNoise() {
        // Values like 97% or 2400 ms say nothing about which runbook applies.
        assertThat(builder.build("orders-db cpu 97% > 90% for 3 min, p95 2400 ms"))
                .contains("orders | db | cpu | min | p95");
    }

    @Test
    void splitsIdentifiersIntoSearchableWords() {
        assertThat(builder.build("max_connections reached; HikariPool-1 timeout"))
                .contains("max | connections | reached | hikaripool | timeout");
    }

    @Test
    void neverPassesOperatorsOrQuotesThrough() {
        // to_tsquery has its own syntax; user text must not be able to inject into it.
        assertThat(builder.build("a & b | !c ' : (d) <-> e"))
                .isEmpty();
    }

    @Test
    void capsTheNumberOfTerms() {
        String many = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima";

        assertThat(builder.build(many)).hasValueSatisfying(q -> assertThat(q.split(" \\| ")).hasSize(10));
    }

    @Test
    void returnsEmptyForTextWithNothingSearchable() {
        assertThat(builder.build("97% > 90%")).isEqualTo(Optional.empty());
    }
}
