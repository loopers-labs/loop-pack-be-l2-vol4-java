package com.loopers.benchmark.ranking;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

record RankingBenchmarkConfig(
    List<Integer> cardinalities,
    List<Integer> pageSizes,
    int concurrency,
    int iterations,
    int warmup,
    int runs,
    Path outputDir,
    String label
) {
    private static final String CARDINALITIES = "rankingBenchmarkCardinalities";
    private static final String PAGE_SIZES = "rankingBenchmarkPageSizes";
    private static final String CONCURRENCY = "rankingBenchmarkConcurrency";
    private static final String ITERATIONS = "rankingBenchmarkIterations";
    private static final String WARMUP = "rankingBenchmarkWarmup";
    private static final String RUNS = "rankingBenchmarkRuns";
    private static final String OUTPUT_DIR = "rankingBenchmarkOutputDir";
    private static final String LABEL = "rankingBenchmarkLabel";

    RankingBenchmarkConfig {
        cardinalities = positiveValues(cardinalities, CARDINALITIES, Integer.MAX_VALUE);
        pageSizes = positiveValues(pageSizes, PAGE_SIZES, 100);
        if (cardinalities.stream().mapToInt(Integer::intValue).min().orElseThrow()
            < pageSizes.stream().mapToInt(Integer::intValue).max().orElseThrow()) {
            throw new IllegalArgumentException(CARDINALITIES + " must be greater than or equal to every configured page size");
        }
        requirePositive(concurrency, CONCURRENCY);
        requirePositive(iterations, ITERATIONS);
        if (warmup < 0) throw new IllegalArgumentException(WARMUP + " must be zero or greater");
        requirePositive(runs, RUNS);
        if (outputDir == null) throw new IllegalArgumentException(OUTPUT_DIR + " must not be null");
        if (label == null || label.isBlank()) throw new IllegalArgumentException(LABEL + " must not be blank");
        label = label.trim();
    }

    static RankingBenchmarkConfig fromSystemProperties() {
        return from(Map.of(
            CARDINALITIES, System.getProperty(CARDINALITIES, "1000,10000,100000"),
            PAGE_SIZES, System.getProperty(PAGE_SIZES, "20,100"),
            CONCURRENCY, System.getProperty(CONCURRENCY, "1"),
            ITERATIONS, System.getProperty(ITERATIONS, "200"),
            WARMUP, System.getProperty(WARMUP, "20"),
            RUNS, System.getProperty(RUNS, "2"),
            OUTPUT_DIR, System.getProperty(OUTPUT_DIR, "build/reports/ranking-api"),
            LABEL, System.getProperty(LABEL, "local")
        ));
    }

    static RankingBenchmarkConfig from(Map<String, String> values) {
        if (values == null) throw new IllegalArgumentException("benchmark properties must not be null");
        return new RankingBenchmarkConfig(
            integerList(values.getOrDefault(CARDINALITIES, "1000,10000,100000"), CARDINALITIES),
            integerList(values.getOrDefault(PAGE_SIZES, "20,100"), PAGE_SIZES),
            integer(values, CONCURRENCY, 1),
            integer(values, ITERATIONS, 200),
            integer(values, WARMUP, 20),
            integer(values, RUNS, 2),
            Path.of(values.getOrDefault(OUTPUT_DIR, "build/reports/ranking-api").trim()),
            values.getOrDefault(LABEL, "local")
        );
    }

    int expectedResultCount() {
        return cardinalities.size() * pageSizes.size() * runs * 2;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String raw = values.getOrDefault(key, Integer.toString(fallback));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, exception);
        }
    }

    private static List<Integer> integerList(String raw, String key) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(key + " must not be blank");
        try {
            return Arrays.stream(raw.split(",")).map(String::trim).map(Integer::valueOf).toList();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must contain integers: " + raw, exception);
        }
    }

    private static List<Integer> positiveValues(List<Integer> values, String key, int maximum) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException(key + " must not be empty");
        if (values.stream().anyMatch(value -> value == null || value <= 0 || value > maximum)) {
            throw new IllegalArgumentException(key + " must contain values between 1 and " + maximum);
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(key + " must not contain duplicate values");
        }
        return List.copyOf(values);
    }

    private static void requirePositive(int value, String key) {
        if (value <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
    }
}
