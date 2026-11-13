package com.smartAiUniversityAssistant.seniorproject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The Flyway versions in src/main/resources/db/migration, read from the classpath, so the migration tests check the
 * history against the files that exist instead of a number that every new migration has to bump.
 */
public final class MigrationVersions {
    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");
    private static final List<Integer> ALL = load();

    private MigrationVersions() {
    }

    /** How many versioned migrations exist (they are numbered without gaps from V1). */
    public static int count() { return ALL.size(); }

    /** The versions from {@code first} on, in order, as Flyway writes them into flyway_schema_history. */
    public static List<String> from(int first) {
        return ALL.stream().filter(v -> v >= first).map(String::valueOf).toList();
    }

    private static List<Integer> load() {
        try {
            var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
            return Stream.of(resources).map(r -> VERSIONED.matcher(r.getFilename() == null ? "" : r.getFilename()))
                    .filter(java.util.regex.Matcher::matches).map(m -> Integer.parseInt(m.group(1))).distinct().sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
