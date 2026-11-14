package dev.cloudpilot.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Markdown runbook into sections for retrieval.
 *
 * Each chunk keeps its full heading path ("Runbook > Mitigation > Short term"). That path is
 * what makes a chunk understandable on its own, both for the search ranking (heading words
 * weigh more) and later for the AI, which must cite exactly which section it used.
 */
public class MarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private final int maxChars;

    public MarkdownChunker(int maxChars) {
        this.maxChars = maxChars;
    }

    public record Chunk(int ordinal, String heading, String content) {
    }

    private record Section(String heading, String content) {
    }

    public List<Chunk> chunk(String title, String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections(title, markdown)) {
            List<String> parts = split(section.content());
            for (int i = 0; i < parts.size(); i++) {
                String heading = i == 0 ? section.heading() : section.heading() + " (part " + (i + 1) + ")";
                chunks.add(new Chunk(chunks.size(), heading, parts.get(i)));
            }
        }
        return chunks;
    }

    private List<Section> sections(String title, String markdown) {
        List<Section> sections = new ArrayList<>();
        // path[level] = heading text at that level; level 1 (#) is the document title.
        String[] path = new String[7];
        String currentHeading = title;
        StringBuilder body = new StringBuilder();
        boolean inCode = false;

        for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                inCode = !inCode;
            }
            Matcher heading = inCode ? null : HEADING.matcher(line);
            if (heading != null && heading.matches()) {
                addSection(sections, currentHeading, body);
                int level = heading.group(1).length();
                path[level] = heading.group(2).trim();
                for (int deeper = level + 1; deeper < path.length; deeper++) {
                    path[deeper] = null;
                }
                currentHeading = headingPath(title, path);
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        addSection(sections, currentHeading, body);
        return sections;
    }

    /** Title first, then every heading below level 1 (a level-1 heading usually repeats the title). */
    private static String headingPath(String title, String[] path) {
        StringBuilder result = new StringBuilder(title);
        for (int level = 2; level < path.length; level++) {
            if (path[level] != null) {
                result.append(" > ").append(path[level]);
            }
        }
        return result.toString();
    }

    private static void addSection(List<Section> sections, String heading, StringBuilder body) {
        String content = body.toString().strip();
        if (!content.isEmpty()) {
            sections.add(new Section(heading, content));
        }
    }

    /** Splits on blank lines so no part is longer than maxChars (unless one paragraph is). */
    private List<String> split(String content) {
        if (content.length() <= maxChars) {
            return List.of(content);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\n\\s*\n")) {
            if (!current.isEmpty() && current.length() + 2 + paragraph.length() > maxChars) {
                parts.add(current.toString().strip());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }
}
