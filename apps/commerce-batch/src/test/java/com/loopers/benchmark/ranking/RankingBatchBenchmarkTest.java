package com.loopers.benchmark.ranking;

import com.loopers.batch.job.ranking.DailyRankingMetric;
import com.loopers.batch.job.ranking.DailyRankingMetricReader;
import com.loopers.batch.job.ranking.DailyRankingSnapshotPublisher.DailyRankingScore;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import com.loopers.ranking.RankingScoreFormula;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("benchmark")
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.batch.job.enabled=false",
    "spring.jpa.show-sql=false"
})
class RankingBatchBenchmarkTest {
    private static final LocalDate BENCHMARK_DATE = LocalDate.of(2099, 1, 2);
    private static final int INSERT_BATCH_SIZE = 1_000;

    private final DailyRankingMetricReader metricReader;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final BenchmarkEquivalentSnapshotPublisher publisher;
    private final AtomicLong executionIds = new AtomicLong(10_000);

    @Autowired
    RankingBatchBenchmarkTest(
        DailyRankingMetricReader metricReader,
        JdbcTemplate jdbcTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.metricReader = metricReader;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.publisher = new BenchmarkEquivalentSnapshotPublisher(redisTemplate);
    }

    @Test
    void measuresSnapshotThroughputRecoveryAndDerivedFreshness() throws Exception {
        RankingBatchBenchmarkConfig config = RankingBatchBenchmarkConfig.fromSystemProperties();
        List<RankingBatchBenchmarkResult> throughput = new ArrayList<>();

        for (int cardinality : config.cardinalities()) {
            SeedSummary seed = seed(cardinality, config.activeHours());
            List<DailyRankingScore> precheckedScores = scores(metricReader.read(BENCHMARK_DATE));
            assertThat(precheckedScores).hasSize(cardinality);
            assertThat(redisTemplate.opsForZSet().size(rankingKey())).isIn(null, 0L);

            for (int warmup = 0; warmup < config.warmupRuns(); warmup++) {
                runMeasured(seed, config.chunkSizes().get(0), 0);
            }
            for (int run = 1; run <= config.runs(); run++) {
                for (int chunkSize : rotated(config.chunkSizes(), run - 1)) {
                    throughput.add(runMeasured(seed, chunkSize, run));
                }
            }
        }

        List<RankingBatchRecoveryResult> recovery = recovery(config);
        List<RankingBatchFreshnessResult> freshness = freshness(config, throughput);
        RankingBatchBenchmarkReport.ReportFiles files = RankingBatchBenchmarkReport.write(
            config, List.copyOf(throughput), recovery, freshness, Instant.now()
        );

        assertThat(throughput).hasSize(config.cardinalities().size() * config.chunkSizes().size() * config.runs());
        assertThat(throughput).allMatch(result -> result.status().equals("success"));
        assertThat(recovery).allMatch(result -> result.rerunExact()
            && result.canonicalUnchangedOnFailure() && result.retryExact());
        files.verifyWritten();
        cleanupBenchmarkData();
    }

    private RankingBatchBenchmarkResult runMeasured(SeedSummary seed, int chunkSize, int run) {
        redisTemplate.delete(rankingKey());
        long startedAt = System.nanoTime();
        List<DailyRankingScore> scores = scores(metricReader.read(BENCHMARK_DATE));
        BenchmarkEquivalentSnapshotPublisher.PublishOutcome outcome = publisher.publish(
            BENCHMARK_DATE, executionIds.incrementAndGet(), scores, chunkSize, -1
        );
        double elapsedMs = elapsedMs(startedAt);

        Correctness correctness = verifyPublished(scores, seed.cardinality());
        return new RankingBatchBenchmarkResult(
            run,
            seed.cardinality(),
            seed.metricUnits(),
            seed.sourceRows(),
            seed.activeHours(),
            chunkSize,
            elapsedMs,
            rate(seed.metricUnits(), elapsedMs),
            rate(seed.cardinality(), elapsedMs),
            outcome.memberMutations(),
            outcome.chunkCount(),
            correctness.totalScore(),
            correctness.digest(),
            "success"
        );
    }

    private List<RankingBatchRecoveryResult> recovery(RankingBatchBenchmarkConfig config) {
        int cardinality = config.cardinalities().stream().max(Integer::compareTo).orElseThrow();
        seed(cardinality, config.activeHours());
        List<DailyRankingScore> scores = scores(metricReader.read(BENCHMARK_DATE));
        List<RankingBatchRecoveryResult> results = new ArrayList<>();
        for (int chunkSize : config.chunkSizes()) {
            redisTemplate.delete(rankingKey());
            publisher.publish(BENCHMARK_DATE, executionIds.incrementAndGet(), scores, chunkSize, -1);
            String baseline = redisDigest();

            publisher.publish(BENCHMARK_DATE, executionIds.incrementAndGet(), scores, chunkSize, -1);
            String rerun = redisDigest();

            int failAfterChunks = Math.max(1, (scores.size() + chunkSize - 1) / chunkSize / 2);
            long failingExecutionId = executionIds.incrementAndGet();
            try {
                publisher.publish(BENCHMARK_DATE, failingExecutionId, scores, chunkSize, failAfterChunks);
                throw new AssertionError("failure injection did not trigger");
            } catch (BenchmarkEquivalentSnapshotPublisher.InjectedBuildFailure expected) {
                assertThat(expected.completedChunks()).isEqualTo(failAfterChunks);
            }
            String afterFailure = redisDigest();

            publisher.publish(BENCHMARK_DATE, failingExecutionId, scores, chunkSize, -1);
            String afterRetry = redisDigest();
            results.add(new RankingBatchRecoveryResult(
                cardinality, chunkSize, baseline, rerun, afterFailure, afterRetry,
                baseline.equals(rerun), baseline.equals(afterFailure), baseline.equals(afterRetry)
            ));
        }
        return List.copyOf(results);
    }

    private List<RankingBatchFreshnessResult> freshness(
        RankingBatchBenchmarkConfig config,
        List<RankingBatchBenchmarkResult> throughput
    ) {
        int cardinality = config.cardinalities().stream().max(Integer::compareTo).orElseThrow();
        int chunkSize = config.chunkSizes().stream()
            .min(Comparator.comparingInt(value -> Math.abs(value - BenchmarkEquivalentSnapshotPublisher.PRODUCTION_CHUNK_SIZE)))
            .orElseThrow();
        List<Double> runtimes = throughput.stream()
            .filter(result -> result.cardinality() == cardinality && result.chunkSize() == chunkSize)
            .map(RankingBatchBenchmarkResult::elapsedMs)
            .toList();
        double p50 = RankingBatchStatistics.percentile(runtimes, 50);
        double p95 = RankingBatchStatistics.percentile(runtimes, 95);
        double max = RankingBatchStatistics.percentile(runtimes, 100);
        return config.freshnessIntervalsSeconds().stream()
            .map(interval -> new RankingBatchFreshnessResult(
                interval, cardinality, chunkSize, p50, p95, max,
                interval * 1_000.0 + p50, interval * 1_000.0 + p95, interval * 1_000.0 + max
            ))
            .toList();
    }

    private SeedSummary seed(int cardinality, int activeHours) {
        cleanupBenchmarkData();
        long[] metricUnits = {0L};
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("""
                INSERT INTO product_metric_hourly
                    (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                """)) {
                int batched = 0;
                for (int hour = 0; hour < activeHours; hour++) {
                    for (long productId = 1; productId <= cardinality; productId++) {
                        long views = 1 + Math.floorMod(productId * 31 + hour * 7L, 5);
                        long likes = Math.floorMod(productId * 17 + hour * 3L, 3);
                        long sales = Math.floorMod(productId * 13 + hour, 2);
                        statement.setDate(1, Date.valueOf(BENCHMARK_DATE));
                        statement.setInt(2, hour);
                        statement.setLong(3, productId);
                        statement.setLong(4, likes);
                        statement.setLong(5, views);
                        statement.setLong(6, sales);
                        statement.addBatch();
                        metricUnits[0] += views + likes + sales;
                        if (++batched % INSERT_BATCH_SIZE == 0) {
                            statement.executeBatch();
                        }
                    }
                }
                if (batched % INSERT_BATCH_SIZE != 0) {
                    statement.executeBatch();
                }
            }
            return null;
        });
        return new SeedSummary(cardinality, (long) cardinality * activeHours, metricUnits[0], activeHours);
    }

    private List<DailyRankingScore> scores(List<DailyRankingMetric> metrics) {
        return metrics.stream()
            .map(metric -> new DailyRankingScore(
                metric.productId(),
                RankingScoreFormula.calculate(metric.viewCount(), metric.likeCount(), metric.salesCount())
            ))
            .toList();
    }

    private Correctness verifyPublished(List<DailyRankingScore> expected, int cardinality) {
        assertThat(redisTemplate.opsForZSet().size(rankingKey())).isEqualTo((long) cardinality);
        double totalScore = expected.stream().mapToDouble(DailyRankingScore::score).sum();
        String expectedDigest = scoreDigest(expected);
        String actualDigest = redisDigest();
        assertThat(actualDigest).isEqualTo(expectedDigest);
        return new Correctness(totalScore, actualDigest);
    }

    private String scoreDigest(List<DailyRankingScore> scores) {
        List<ScoreEntry> ordered = scores.stream()
            .map(score -> new ScoreEntry(DailyRankingKey.member(score.productId()), score.score()))
            .sorted(Comparator.comparingDouble(ScoreEntry::score).thenComparing(ScoreEntry::member))
            .toList();
        StringBuilder canonical = new StringBuilder();
        for (ScoreEntry entry : ordered) {
            appendDigestLine(canonical, entry.member(), entry.score());
        }
        return sha256(canonical.toString());
    }

    private String redisDigest() {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(rankingKey(), 0, -1);
        if (tuples == null) {
            throw new IllegalStateException("ranking snapshot does not exist");
        }
        StringBuilder canonical = new StringBuilder();
        for (TypedTuple<String> tuple : tuples) {
            appendDigestLine(canonical, tuple.getValue(), tuple.getScore());
        }
        return sha256(canonical.toString());
    }

    private void appendDigestLine(StringBuilder canonical, String member, double score) {
        canonical.append(member).append(':')
            .append(Long.toHexString(Double.doubleToLongBits(score))).append('\n');
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<Integer> rotated(List<Integer> values, int offset) {
        List<Integer> rotated = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            rotated.add(values.get((index + offset) % values.size()));
        }
        return rotated;
    }

    private void cleanupBenchmarkData() {
        redisTemplate.delete(rankingKey());
        jdbcTemplate.update("DELETE FROM product_metric_hourly WHERE metric_date = ?", BENCHMARK_DATE);
    }

    private String rankingKey() {
        return DailyRankingKey.from(BENCHMARK_DATE);
    }

    private double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private double rate(long count, double elapsedMs) {
        return elapsedMs == 0.0 ? 0.0 : count / (elapsedMs / 1_000.0);
    }

    private record SeedSummary(int cardinality, long sourceRows, long metricUnits, int activeHours) {
    }

    private record Correctness(double totalScore, String digest) {
    }

    private record ScoreEntry(String member, double score) {
    }
}
