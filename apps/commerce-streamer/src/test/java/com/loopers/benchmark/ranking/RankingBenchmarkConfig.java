package com.loopers.benchmark.ranking;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

record RankingBenchmarkConfig(
    int events,
    List<Integer> batchSizes,
    int hotCardinality,
    int uniformCardinality,
    int runs,
    int warmupEvents,
    List<Integer> retryBatchSizes,
    List<Integer> failurePoints,
    Path outputDir,
    String label
) {
    private static final String EVENTS = "rankingBenchmarkEvents";
    private static final String BATCH_SIZES = "rankingBenchmarkBatchSizes";
    private static final String HOT_CARDINALITY = "rankingBenchmarkHotCardinality";
    private static final String UNIFORM_CARDINALITY = "rankingBenchmarkUniformCardinality";
    private static final String RUNS = "rankingBenchmarkRuns";
    private static final String WARMUP_EVENTS = "rankingBenchmarkWarmupEvents";
    private static final String RETRY_BATCH_SIZES = "rankingBenchmarkRetryBatchSizes";
    private static final String FAILURE_POINTS = "rankingBenchmarkFailurePoints";
    private static final String OUTPUT_DIR = "rankingBenchmarkOutputDir";
    private static final String LABEL = "rankingBenchmarkLabel";

    RankingBenchmarkConfig {
        positive(events, EVENTS);
        positive(hotCardinality, HOT_CARDINALITY);
        positive(uniformCardinality, UNIFORM_CARDINALITY);
        positive(runs, RUNS);
        nonNegative(warmupEvents, WARMUP_EVENTS);
        batchSizes = positiveList(batchSizes, BATCH_SIZES);
        retryBatchSizes = positiveList(retryBatchSizes, RETRY_BATCH_SIZES);
        failurePoints = failurePoints(failurePoints);
        if (outputDir == null) {
            throw new IllegalArgumentException(OUTPUT_DIR + " must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(LABEL + " must not be blank");
        }
        label = label.trim();
    }

    static RankingBenchmarkConfig fromSystemProperties() {
        return from(Map.ofEntries(
            entry(EVENTS), entry(BATCH_SIZES), entry(HOT_CARDINALITY), entry(UNIFORM_CARDINALITY),
            entry(RUNS), entry(WARMUP_EVENTS), entry(RETRY_BATCH_SIZES), entry(FAILURE_POINTS),
            entry(OUTPUT_DIR), entry(LABEL)
        ));
    }

    static RankingBenchmarkConfig from(Map<String, String> properties) {
        if (properties == null) {
            throw new IllegalArgumentException("benchmark properties must not be null");
        }
        int events = integer(properties, EVENTS, 10_000);
        return new RankingBenchmarkConfig(
            events,
            integers(properties, BATCH_SIZES, "1,100,1000,3000"),
            integer(properties, HOT_CARDINALITY, 100),
            integer(properties, UNIFORM_CARDINALITY, events),
            integer(properties, RUNS, 2),
            integer(properties, WARMUP_EVENTS, 1_000),
            integers(properties, RETRY_BATCH_SIZES, "50,500,3000"),
            integers(properties, FAILURE_POINTS, "0,25,75"),
            Path.of(value(properties, OUTPUT_DIR, "build/reports/ranking-streamer").trim()),
            value(properties, LABEL, "local")
        );
    }

    private static Map.Entry<String, String> entry(String key) {
        return Map.entry(key, System.getProperty(key, defaultValue(key)));
    }

    private static String defaultValue(String key) {
        return switch (key) {
            case EVENTS -> "10000";
            case BATCH_SIZES -> "1,100,1000,3000";
            case HOT_CARDINALITY -> "100";
            case UNIFORM_CARDINALITY -> System.getProperty(EVENTS, "10000");
            case RUNS -> "2";
            case WARMUP_EVENTS -> "1000";
            case RETRY_BATCH_SIZES -> "50,500,3000";
            case FAILURE_POINTS -> "0,25,75";
            case OUTPUT_DIR -> "build/reports/ranking-streamer";
            case LABEL -> "local";
            default -> throw new IllegalArgumentException("Unknown benchmark property: " + key);
        };
    }

    private static int integer(Map<String, String> properties, String key, int defaultValue) {
        String raw = value(properties, key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, exception);
        }
    }

    private static List<Integer> integers(Map<String, String> properties, String key, String defaultValue) {
        String raw = value(properties, key, defaultValue);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        List<Integer> values = new ArrayList<>();
        for (String item : raw.split(",")) {
            if (item.isBlank()) {
                throw new IllegalArgumentException(key + " contains an empty value");
            }
            try {
                values.add(Integer.parseInt(item.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must contain integers: " + item, exception);
            }
        }
        return values;
    }

    private static String value(Map<String, String> properties, String key, String defaultValue) {
        return properties.containsKey(key) ? properties.get(key) : defaultValue;
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

    private static List<Integer> failurePoints(List<Integer> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value < 0 || value >= 100)) {
            throw new IllegalArgumentException(FAILURE_POINTS + " must be between 0 and 99");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(FAILURE_POINTS + " must not contain duplicate values");
        }
        return List.copyOf(values);
    }

    private static void positive(int value, String key) {
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
    }

    private static void nonNegative(int value, String key) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be zero or greater");
        }
    }
}
