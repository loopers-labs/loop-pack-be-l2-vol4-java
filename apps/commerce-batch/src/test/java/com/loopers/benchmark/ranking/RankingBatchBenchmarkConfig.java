package com.loopers.benchmark.ranking;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

record RankingBatchBenchmarkConfig(
    List<Integer> cardinalities,
    int activeHours,
    List<Integer> chunkSizes,
    int runs,
    int warmupRuns,
    List<Integer> freshnessIntervalsSeconds,
    Path outputDir,
    String label
) {
    private static final String CARDINALITIES = "rankingBatchBenchmarkCardinalities";
    private static final String ACTIVE_HOURS = "rankingBatchBenchmarkActiveHours";
    private static final String CHUNK_SIZES = "rankingBatchBenchmarkChunkSizes";
    private static final String RUNS = "rankingBatchBenchmarkRuns";
    private static final String WARMUP = "rankingBatchBenchmarkWarmup";
    private static final String FRESHNESS_INTERVALS = "rankingBatchBenchmarkFreshnessIntervals";
    private static final String OUTPUT_DIR = "rankingBatchBenchmarkOutputDir";
    private static final String LABEL = "rankingBatchBenchmarkLabel";

    RankingBatchBenchmarkConfig {
        cardinalities = positiveList(cardinalities, CARDINALITIES);
        if (activeHours < 1 || activeHours > 24) {
            throw new IllegalArgumentException(ACTIVE_HOURS + " must be between 1 and 24");
        }
        chunkSizes = positiveList(chunkSizes, CHUNK_SIZES);
        positive(runs, RUNS);
        if (warmupRuns < 0) {
            throw new IllegalArgumentException(WARMUP + " must be zero or greater");
        }
        freshnessIntervalsSeconds = positiveList(freshnessIntervalsSeconds, FRESHNESS_INTERVALS);
        if (outputDir == null) {
            throw new IllegalArgumentException(OUTPUT_DIR + " must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(LABEL + " must not be blank");
        }
        label = label.trim();
    }

    static RankingBatchBenchmarkConfig fromSystemProperties() {
        return from(Map.ofEntries(
            entry(CARDINALITIES), entry(ACTIVE_HOURS), entry(CHUNK_SIZES), entry(RUNS),
            entry(WARMUP), entry(FRESHNESS_INTERVALS), entry(OUTPUT_DIR), entry(LABEL)
        ));
    }

    static RankingBatchBenchmarkConfig from(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("benchmark properties must not be null");
        }
        return new RankingBatchBenchmarkConfig(
            integers(values, CARDINALITIES, "100,10000,100000"),
            integer(values, ACTIVE_HOURS, 1),
            integers(values, CHUNK_SIZES, "100,1000,3000"),
            integer(values, RUNS, 5),
            integer(values, WARMUP, 1),
            integers(values, FRESHNESS_INTERVALS, "1,10,60"),
            Path.of(value(values, OUTPUT_DIR, "build/reports/ranking-batch").trim()),
            value(values, LABEL, "local")
        );
    }

    private static Map.Entry<String, String> entry(String key) {
        return Map.entry(key, System.getProperty(key, defaultValue(key)));
    }

    private static String defaultValue(String key) {
        return switch (key) {
            case CARDINALITIES -> "100,10000,100000";
            case ACTIVE_HOURS -> "1";
            case CHUNK_SIZES -> "100,1000,3000";
            case RUNS -> "5";
            case WARMUP -> "1";
            case FRESHNESS_INTERVALS -> "1,10,60";
            case OUTPUT_DIR -> "build/reports/ranking-batch";
            case LABEL -> "local";
            default -> throw new IllegalArgumentException("Unknown benchmark property: " + key);
        };
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String raw = value(values, key, Integer.toString(fallback));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, exception);
        }
    }

    private static List<Integer> integers(Map<String, String> values, String key, String fallback) {
        String raw = value(values, key, fallback);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        List<Integer> parsed = new ArrayList<>();
        for (String item : raw.split(",")) {
            try {
                parsed.add(Integer.parseInt(item.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must contain integers: " + item, exception);
            }
        }
        return parsed;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        return values.containsKey(key) ? values.get(key) : fallback;
    }

    private static List<Integer> positiveList(List<Integer> values, String key) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(key + " must contain only positive values");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(key + " must not contain duplicate values");
        }
        return List.copyOf(values);
    }

    private static void positive(int value, String key) {
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
    }
}
