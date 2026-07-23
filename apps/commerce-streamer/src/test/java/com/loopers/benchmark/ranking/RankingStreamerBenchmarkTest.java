package com.loopers.benchmark.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.ranking.CatalogRankingEventProcessor;
import com.loopers.application.ranking.RankingScorePolicy;
import com.loopers.application.ranking.RankingScoreWriter;
import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.ranking.RedisRankingScoreWriter;
import com.loopers.ranking.DailyRankingKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Tag("benchmark")
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.jpa.show-sql=false",
    "spring.kafka.listener.auto-startup=false",
    "management.server.port=0"
})
class RankingStreamerBenchmarkTest {
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2099-01-01T12:00:00+09:00");
    private static final String RANKING_KEY = DailyRankingKey.from(OCCURRED_AT);

    private final RedisRankingScoreWriter redisWriter;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    RankingStreamerBenchmarkTest(
        RedisRankingScoreWriter redisWriter,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisWriter = redisWriter;
        this.redisTemplate = redisTemplate;
    }

    @Test
    void measuresAggregationAndRetryDriftAndWritesReports() throws Exception {
        RankingBenchmarkConfig config = RankingBenchmarkConfig.fromSystemProperties();
        List<RankingAggregationBenchmarkResult> aggregationResults = runAggregationScenarios(config);
        List<RankingRetryDriftBenchmarkResult> retryResults = runRetryScenarios(config);

        RankingBenchmarkReport.ReportFiles reportFiles = RankingBenchmarkReport.write(
            config, aggregationResults, retryResults, Instant.now()
        );

        assertThat(aggregationResults).hasSize(config.batchSizes().size() * 2 * config.runs());
        assertThat(retryResults).hasSize(config.retryBatchSizes().size() * config.failurePoints().size());
        reportFiles.verifyWritten();
        cleanupKey();
    }

    private List<RankingAggregationBenchmarkResult> runAggregationScenarios(RankingBenchmarkConfig config) {
        warmUp(config);
        List<RankingAggregationBenchmarkResult> results = new ArrayList<>();
        for (int run = 1; run <= config.runs(); run++) {
            List<Integer> batchSizes = rotated(config.batchSizes(), run - 1);
            for (String distribution : List.of("hot", "uniform")) {
                int cardinality = Math.min(
                    distribution.equals("hot") ? config.hotCardinality() : config.uniformCardinality(),
                    config.events()
                );
                List<CatalogEventMessage> events = viewEvents(config.events(), cardinality);
                for (int batchSize : batchSizes) {
                    results.add(runAggregationScenario(distribution, batchSize, run, cardinality, events));
                }
            }
        }
        return List.copyOf(results);
    }

    private RankingAggregationBenchmarkResult runAggregationScenario(
        String distribution,
        int batchSize,
        int run,
        int cardinality,
        List<CatalogEventMessage> events
    ) {
        cleanupKey();
        CountingWriter countingWriter = new CountingWriter(redisWriter);
        CatalogRankingEventProcessor processor = processor(countingWriter);
        List<Double> batchLatenciesMs = new ArrayList<>();

        long startedAt = System.nanoTime();
        for (int from = 0; from < events.size(); from += batchSize) {
            int to = Math.min(from + batchSize, events.size());
            long batchStartedAt = System.nanoTime();
            processor.process(events.subList(from, to));
            batchLatenciesMs.add(elapsedMs(batchStartedAt));
        }
        double elapsedMs = elapsedMs(startedAt);

        long expectedUpdates = expectedRedisUpdates(events, batchSize);
        double expectedTotal = events.size() * 0.1;
        assertThat(countingWriter.updates()).isEqualTo(expectedUpdates);
        assertRedisScores(events, expectedTotal);
        return new RankingAggregationBenchmarkResult(
            distribution,
            batchSize,
            run,
            events.size(),
            cardinality,
            countingWriter.updates(),
            elapsedMs,
            rate(events.size(), elapsedMs),
            (double) countingWriter.updates() / events.size(),
            0.1,
            RankingBenchmarkStatistics.summarize(batchLatenciesMs),
            expectedTotal
        );
    }

    private void warmUp(RankingBenchmarkConfig config) {
        if (config.warmupEvents() == 0) {
            return;
        }
        cleanupKey();
        CatalogRankingEventProcessor processor = processor(redisWriter);
        List<CatalogEventMessage> events = viewEvents(
            config.warmupEvents(), Math.min(config.hotCardinality(), config.warmupEvents())
        );
        int batchSize = config.batchSizes().get(0);
        for (int from = 0; from < events.size(); from += batchSize) {
            processor.process(events.subList(from, Math.min(from + batchSize, events.size())));
        }
        cleanupKey();
    }

    private List<RankingRetryDriftBenchmarkResult> runRetryScenarios(RankingBenchmarkConfig config) {
        List<RankingRetryDriftBenchmarkResult> results = new ArrayList<>();
        int products = Math.min(config.events(), config.retryBatchSizes().stream().mapToInt(Integer::intValue).max().orElseThrow());
        List<CatalogEventMessage> workload = differentiatedOrderEvents(config.events(), products);
        Map<Long, Double> expected = expectedScores(workload);
        for (int batchSize : config.retryBatchSizes()) {
            for (int failurePoint : config.failurePoints()) {
                results.add(runRetryScenario(workload, expected, batchSize, failurePoint));
            }
        }
        return List.copyOf(results);
    }

    private RankingRetryDriftBenchmarkResult runRetryScenario(
        List<CatalogEventMessage> workload,
        Map<Long, Double> expected,
        int batchSize,
        int failurePoint
    ) {
        FailingInMemoryWriter writer = new FailingInMemoryWriter();
        CatalogRankingEventProcessor processor = processor(writer);
        int batchCount = (workload.size() + batchSize - 1) / batchSize;
        int failingBatch = batchCount / 2;

        for (int batchIndex = 0, from = 0; from < workload.size(); batchIndex++, from += batchSize) {
            List<CatalogEventMessage> batch = workload.subList(from, Math.min(from + batchSize, workload.size()));
            if (batchIndex == failingBatch) {
                int aggregateWrites = (int) batch.stream().map(CatalogEventMessage::productId).distinct().count();
                int successfulWritesBeforeFailure = Math.min(
                    aggregateWrites - 1,
                    (int) Math.floor(aggregateWrites * failurePoint / 100.0)
                );
                writer.failAfter(successfulWritesBeforeFailure);
                try {
                    processor.process(batch);
                    throw new IllegalStateException("failure injection did not trigger");
                } catch (InjectedFailure expectedFailure) {
                    writer.disableFailure();
                    processor.process(batch);
                }
            } else {
                processor.process(batch);
            }
        }

        double expectedTotal = total(expected);
        double actualTotal = total(writer.scores());
        int driftProducts = (int) expected.entrySet().stream()
            .filter(entry -> Math.abs(entry.getValue() - writer.scores().getOrDefault(entry.getKey(), 0.0)) > 0.000_001)
            .count();
        return new RankingRetryDriftBenchmarkResult(
            batchSize,
            failurePoint,
            workload.size(),
            expected.size(),
            expectedTotal,
            actualTotal,
            expectedTotal == 0.0 ? 0.0 : (actualTotal - expectedTotal) / expectedTotal * 100.0,
            driftProducts,
            top20Overlap(expected, writer.scores())
        );
    }

    private CatalogRankingEventProcessor processor(RankingScoreWriter writer) {
        return new CatalogRankingEventProcessor(new RankingScorePolicy(), writer, new SimpleMeterRegistry());
    }

    private List<CatalogEventMessage> viewEvents(int eventCount, int cardinality) {
        List<CatalogEventMessage> events = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            long productId = index % cardinality + 1L;
            events.add(event("view-" + index, "PRODUCT_VIEWED", productId, Map.of("viewCountDelta", 1)));
        }
        return List.copyOf(events);
    }

    private List<CatalogEventMessage> differentiatedOrderEvents(int eventCount, int products) {
        List<CatalogEventMessage> events = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            long productId = index % products + 1L;
            int quantity = 1 + (int) ((productId * 37 + productId * productId * 13) % 997);
            events.add(event("order-" + index, "PRODUCT_ORDERED", productId, Map.of("salesCountDelta", quantity)));
        }
        return List.copyOf(events);
    }

    private CatalogEventMessage event(String eventId, String type, long productId, Map<String, Object> data) {
        return new CatalogEventMessage(eventId, type, "PRODUCT", productId, OCCURRED_AT, data);
    }

    private long expectedRedisUpdates(List<CatalogEventMessage> events, int batchSize) {
        long updates = 0;
        for (int from = 0; from < events.size(); from += batchSize) {
            updates += events.subList(from, Math.min(from + batchSize, events.size())).stream()
                .map(CatalogEventMessage::productId)
                .distinct()
                .count();
        }
        return updates;
    }

    private void assertRedisScores(List<CatalogEventMessage> events, double expectedTotal) {
        Map<Long, Long> eventCountByProduct = new HashMap<>();
        events.forEach(event -> eventCountByProduct.merge(event.productId(), 1L, Long::sum));
        assertThat(redisTemplate.opsForZSet().zCard(RANKING_KEY)).isEqualTo(eventCountByProduct.size());
        double actualTotal = 0.0;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(RANKING_KEY, 0, -1);
        assertThat(tuples).isNotNull();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            long productId = Long.parseLong(tuple.getValue());
            double expectedScore = eventCountByProduct.get(productId) * 0.1;
            assertThat(tuple.getScore()).isCloseTo(expectedScore, within(0.000_001));
            actualTotal += tuple.getScore();
        }
        assertThat(actualTotal).isCloseTo(expectedTotal, within(0.000_001));
    }

    private Map<Long, Double> expectedScores(List<CatalogEventMessage> workload) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        RankingScorePolicy policy = new RankingScorePolicy();
        workload.forEach(event -> scores.merge(event.productId(), policy.score(event), Double::sum));
        return scores;
    }

    private double top20Overlap(Map<Long, Double> expected, Map<Long, Double> actual) {
        Set<Long> expectedTop = new HashSet<>(top(expected, 20));
        Set<Long> actualTop = new HashSet<>(top(actual, 20));
        expectedTop.retainAll(actualTop);
        int denominator = Math.min(20, expected.size());
        return denominator == 0 ? 100.0 : expectedTop.size() * 100.0 / denominator;
    }

    private List<Long> top(Map<Long, Double> scores, int limit) {
        return scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    private List<Integer> rotated(List<Integer> values, int offset) {
        List<Integer> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.get((index + offset) % values.size()));
        }
        return result;
    }

    private double total(Map<Long, Double> scores) {
        return scores.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private double rate(int events, double elapsedMs) {
        return events * 1_000.0 / elapsedMs;
    }

    private void cleanupKey() {
        redisTemplate.delete(RANKING_KEY);
    }

    private static final class CountingWriter implements RankingScoreWriter {
        private final RankingScoreWriter delegate;
        private final AtomicLong updates = new AtomicLong();

        private CountingWriter(RankingScoreWriter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void increment(String rankingKey, Long productId, double scoreDelta) {
            delegate.increment(rankingKey, productId, scoreDelta);
            updates.incrementAndGet();
        }

        long updates() {
            return updates.get();
        }
    }

    private static final class FailingInMemoryWriter implements RankingScoreWriter {
        private final Map<Long, Double> scores = new LinkedHashMap<>();
        private int successfulWrites;
        private int failAfter = Integer.MAX_VALUE;

        @Override
        public void increment(String rankingKey, Long productId, double scoreDelta) {
            if (successfulWrites == failAfter) {
                throw new InjectedFailure();
            }
            scores.merge(productId, scoreDelta, Double::sum);
            successfulWrites++;
        }

        void failAfter(int successfulWritesBeforeFailure) {
            successfulWrites = 0;
            failAfter = successfulWritesBeforeFailure;
        }

        void disableFailure() {
            successfulWrites = 0;
            failAfter = Integer.MAX_VALUE;
        }

        Map<Long, Double> scores() {
            return scores;
        }
    }

    private static final class InjectedFailure extends RuntimeException {
    }
}
