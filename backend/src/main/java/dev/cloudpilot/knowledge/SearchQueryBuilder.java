package dev.cloudpilot.knowledge;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free text (alert messages, log lines, a question) into a PostgreSQL tsquery that
 * matches chunks containing ANY of the meaningful words, ranked by how many match.
 *
 * Only plain alphanumeric words get through, so nothing in the text can inject tsquery
 * operators. Stemming and stop words are handled by PostgreSQL's 'english' configuration.
 */
public class SearchQueryBuilder {

    /** Words of letters/digits; underscores and hyphens split identifiers into words. */
    private static final Pattern WORD = Pattern.compile("[a-z][a-z0-9]*");

    /**
     * PostgreSQL drops stop words itself, but they would still use up the term budget,
     * crowding out words that matter.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "to", "of", "in", "on", "at", "is", "it", "by", "with", "from", "was", "are",
            "be", "as", "or", "an", "after", "why", "what", "did", "does", "this", "that");

    private final int maxTerms;

    public SearchQueryBuilder(int maxTerms) {
        this.maxTerms = maxTerms;
    }

    public Optional<String> build(String text) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find() && terms.size() < maxTerms) {
            String word = matcher.group();
            if (word.length() >= 2 && !isUnit(word) && !STOP_WORDS.contains(word)) {
                terms.add(word);
            }
        }
        return terms.isEmpty() ? Optional.empty() : Optional.of(String.join(" | ", terms));
    }

    /** Units that come with numbers in metric messages carry no meaning on their own. */
    private static boolean isUnit(String word) {
        return switch (word) {
            case "ms", "s", "gib", "mib", "kb", "mb", "gb" -> true;
            default -> false;
        };
    }
}
