package com.loopers.benchmark.queue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record WaitingQueueBenchmarkConfig(
    int users,
    int concurrency,
    List<Integer> batchSizes,
    List<Long> admitDelaysMs,
    int runs,
    int warmupUsers,
    int dbPoolSize,
    Path outputDir,
    String label
) {
    private static final String USERS = "queueBenchmarkUsers";
    private static final String CONCURRENCY = "queueBenchmarkConcurrency";
    private static final String BATCH_SIZES = "queueBenchmarkBatchSizes";
    private static final String ADMIT_DELAYS_MS = "queueBenchmarkAdmitDelaysMs";
    private static final String RUNS = "queueBenchmarkRuns";
    private static final String WARMUP_USERS = "queueBenchmarkWarmupUsers";
    private static final String DB_POOL_SIZE = "queueBenchmarkDbPoolSize";
    private static final String OUTPUT_DIR = "queueBenchmarkOutputDir";
    private static final String LABEL = "queueBenchmarkLabel";

    WaitingQueueBenchmarkConfig {
        requirePositive(users, USERS);
        requirePositive(concurrency, CONCURRENCY);
        requirePositive(runs, RUNS);
        requireNonNegative(warmupUsers, WARMUP_USERS);
        requirePositive(dbPoolSize, DB_POOL_SIZE);
        batchSizes = immutablePositiveValues(batchSizes, BATCH_SIZES);
        admitDelaysMs = immutableNonNegativeValues(admitDelaysMs, ADMIT_DELAYS_MS);
        if (outputDir == null) {
            throw new IllegalArgumentException(OUTPUT_DIR + " must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(LABEL + " must not be blank");
        }
        label = label.trim();
    }

    static WaitingQueueBenchmarkConfig fromSystemProperties() {
        return from(Map.ofEntries(
            propertyEntry(USERS),
            propertyEntry(CONCURRENCY),
            propertyEntry(BATCH_SIZES),
            propertyEntry(ADMIT_DELAYS_MS),
            propertyEntry(RUNS),
            propertyEntry(WARMUP_USERS),
            propertyEntry(DB_POOL_SIZE),
            propertyEntry(OUTPUT_DIR),
            propertyEntry(LABEL)
        ));
    }

    static WaitingQueueBenchmarkConfig from(Map<String, String> properties) {
        if (properties == null) {
            throw new IllegalArgumentException("benchmark properties must not be null");
        }
        return new WaitingQueueBenchmarkConfig(
            parseInt(properties, USERS, 60),
            parseInt(properties, CONCURRENCY, 20),
            parseIntegerList(properties, BATCH_SIZES, "5,10,18"),
            parseLongList(properties, ADMIT_DELAYS_MS, "100"),
            parseInt(properties, RUNS, 2),
            parseInt(properties, WARMUP_USERS, 10),
            parseInt(properties, DB_POOL_SIZE, 10),
            parsePath(properties, OUTPUT_DIR, "build/reports/waiting-queue"),
            value(properties, LABEL, "local")
        );
    }

    int expectedScenarioCount() {
        return batchSizes.size() * admitDelaysMs.size() * runs;
    }

    private static Map.Entry<String, String> propertyEntry(String key) {
        return Map.entry(key, System.getProperty(key, defaultValue(key)));
    }

    private static String defaultValue(String key) {
        return switch (key) {
            case USERS -> "60";
            case CONCURRENCY -> "20";
            case BATCH_SIZES -> "5,10,18";
            case ADMIT_DELAYS_MS -> "100";
            case RUNS -> "2";
            case WARMUP_USERS -> "10";
            case DB_POOL_SIZE -> "10";
            case OUTPUT_DIR -> "build/reports/waiting-queue";
            case LABEL -> "local";
            default -> throw new IllegalArgumentException("Unknown benchmark property: " + key);
        };
    }

    private static int parseInt(Map<String, String> properties, String key, int defaultValue) {
        String value = value(properties, key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + value, exception);
        }
    }

    private static List<Integer> parseIntegerList(Map<String, String> properties, String key, String defaultValue) {
        List<String> values = splitList(value(properties, key, defaultValue), key);
        List<Integer> parsed = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                parsed.add(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must contain integers: " + value, exception);
            }
        }
        return parsed;
    }

    private static List<Long> parseLongList(Map<String, String> properties, String key, String defaultValue) {
        List<String> values = splitList(value(properties, key, defaultValue), key);
        List<Long> parsed = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                parsed.add(Long.parseLong(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " must contain integers: " + value, exception);
            }
        }
        return parsed;
    }

    private static List<String> splitList(String rawValue, String key) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        List<String> values = List.of(rawValue.split(",")).stream()
            .map(String::trim)
            .toList();
        if (values.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(key + " contains an empty value");
        }
        return values;
    }

    private static Path parsePath(Map<String, String> properties, String key, String defaultValue) {
        String value = value(properties, key, defaultValue);
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        try {
            return Path.of(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " is not a valid path: " + value, exception);
        }
    }

    private static String value(Map<String, String> properties, String key, String defaultValue) {
        return properties.containsKey(key) ? properties.get(key) : defaultValue;
    }

    private static List<Integer> immutablePositiveValues(List<Integer> values, String key) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        for (Integer value : values) {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException(key + " must contain only positive values");
            }
        }
        requireUnique(values, key);
        return List.copyOf(values);
    }

    private static List<Long> immutableNonNegativeValues(List<Long> values, String key) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        for (Long value : values) {
            if (value == null || value < 0) {
                throw new IllegalArgumentException(key + " must contain only non-negative values");
            }
        }
        requireUnique(values, key);
        return List.copyOf(values);
    }

    private static void requireUnique(List<?> values, String key) {
        Set<?> uniqueValues = new HashSet<>(values);
        if (uniqueValues.size() != values.size()) {
            throw new IllegalArgumentException(key + " must not contain duplicate values");
        }
    }

    private static void requirePositive(int value, String key) {
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
    }

    private static void requireNonNegative(int value, String key) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be zero or greater");
        }
    }
}
