package com.loopers.benchmark.ranking.period;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

record PeriodRankingBenchmarkConfig(
    List<Integer> cardinalities,
    int periodDays,
    int activeHours,
    int pageSize,
    List<Integer> chunkSizes,
    int runs,
    int warmupRuns,
    int contentionRuns,
    long holdMs,
    Path outputDir,
    String label
) {
    static final String PREFIX = "periodRankingBenchmark";

    PeriodRankingBenchmarkConfig {
        cardinalities = positiveList(cardinalities, "Cardinalities");
        positive(periodDays, "PeriodDays");
        if (activeHours < 1 || activeHours > 24) {
            throw new IllegalArgumentException(key("ActiveHours") + " must be between 1 and 24");
        }
        positive(pageSize, "PageSize");
        chunkSizes = positiveList(chunkSizes, "ChunkSizes");
        positive(runs, "Runs");
        if (warmupRuns < 0) {
            throw new IllegalArgumentException(key("Warmup") + " must be zero or greater");
        }
        positive(contentionRuns, "ContentionRuns");
        if (holdMs < 0) {
            throw new IllegalArgumentException(key("HoldMs") + " must be zero or greater");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException(key("OutputDir") + " must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(key("Label") + " must not be blank");
        }
        label = label.trim();
    }

    static PeriodRankingBenchmarkConfig fromSystemProperties() {
        return from(System.getProperties().entrySet().stream().collect(
            java.util.stream.Collectors.toMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString())
        ));
    }

    static PeriodRankingBenchmarkConfig from(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("benchmark properties must not be null");
        }
        return new PeriodRankingBenchmarkConfig(
            integers(values, "Cardinalities", "1000,5000"),
            integer(values, "PeriodDays", 7),
            integer(values, "ActiveHours", 1),
            integer(values, "PageSize", 100),
            integers(values, "ChunkSizes", "100,500,1000"),
            integer(values, "Runs", 3),
            integer(values, "Warmup", 1),
            integer(values, "ContentionRuns", 3),
            longValue(values, "HoldMs", 400),
            Path.of(value(values, "OutputDir", "build/reports/period-ranking").trim()),
            value(values, "Label", "local")
        );
    }

    List<Integer> chunkWarmupPlan() {
        return IntStream.range(0, warmupRuns)
            .boxed()
            .flatMap(ignored -> chunkSizes.stream())
            .toList();
    }

    private static int integer(Map<String, String> values, String suffix, int fallback) {
        String raw = value(values, suffix, Integer.toString(fallback));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key(suffix) + " must be an integer: " + raw, exception);
        }
    }

    private static long longValue(Map<String, String> values, String suffix, long fallback) {
        String raw = value(values, suffix, Long.toString(fallback));
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key(suffix) + " must be an integer: " + raw, exception);
        }
    }

    private static List<Integer> integers(Map<String, String> values, String suffix, String fallback) {
        String raw = value(values, suffix, fallback);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key(suffix) + " must not be blank");
        }
        List<Integer> parsed = new ArrayList<>();
        for (String item : raw.split(",")) {
            try {
                parsed.add(Integer.parseInt(item.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key(suffix) + " must contain integers: " + item, exception);
            }
        }
        return parsed;
    }

    private static String value(Map<String, String> values, String suffix, String fallback) {
        return values.getOrDefault(key(suffix), fallback);
    }

    private static List<Integer> positiveList(List<Integer> values, String suffix) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(key(suffix) + " must contain only positive values");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(key(suffix) + " must not contain duplicate values");
        }
        return List.copyOf(values);
    }

    private static void positive(int value, String suffix) {
        if (value <= 0) {
            throw new IllegalArgumentException(key(suffix) + " must be greater than zero");
        }
    }

    private static String key(String suffix) {
        return PREFIX + suffix;
    }
}
