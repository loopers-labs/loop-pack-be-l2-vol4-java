package com.loopers.benchmark.ranking.period;

import com.loopers.ranking.RankingScoreFormula;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("period-ranking-benchmark")
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.batch.job.enabled=false",
    "spring.batch.job.name=none",
    "spring.jpa.show-sql=false"
})
class PeriodRankingBenchmarkTest {
    private static final LocalDate PERIOD_START = LocalDate.of(2099, 3, 1);
    private static final String RUN_KEY_PREFIX = "period-bench:";
    private static final int INSERT_BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    PeriodRankingBenchmarkTest(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @Test
    void measuresPeriodAggregationChunkSizesAndIsolationContention() throws Exception {
        PeriodRankingBenchmarkConfig config = PeriodRankingBenchmarkConfig.fromSystemProperties();
        List<PeriodRankingStrategyResult> strategies = new ArrayList<>();
        List<PeriodRankingChunkResult> chunks = new ArrayList<>();
        List<PeriodRankingContentionResult> contention = new ArrayList<>();
        try {
            for (int cardinality : config.cardinalities()) {
                SeedSummary seed = seed(cardinality, config.periodDays(), config.activeHours());
                warmup(seed, config);

                for (int run = 1; run <= config.runs(); run++) {
                    List<String> order = rotated(List.of("legacy", "snapshot"), run - 1);
                    PeriodRankingStrategyResult first = runStrategy(order.get(0), seed, config.pageSize(), run);
                    PeriodRankingStrategyResult second = runStrategy(order.get(1), seed, config.pageSize(), run);
                    assertThat(first.digest()).isEqualTo(second.digest());
                    assertThat(first.stagingRows()).isEqualTo(cardinality);
                    assertThat(second.stagingRows()).isEqualTo(cardinality);
                    strategies.add(first);
                    strategies.add(second);

                    for (int chunkSize : rotated(config.chunkSizes(), run - 1)) {
                        PeriodRankingChunkResult result = runChunk(seed, chunkSize, run);
                        assertThat(result.digest()).isEqualTo(first.digest());
                        chunks.add(result);
                    }
                }
            }

            SeedSummary contentionSeed = seed(
                config.cardinalities().stream().max(Integer::compareTo).orElseThrow(),
                config.periodDays(),
                config.activeHours()
            );
            for (int run = 1; run <= config.contentionRuns(); run++) {
                List<Isolation> order = rotated(List.of(Isolation.REPEATABLE_READ, Isolation.READ_COMMITTED), run - 1);
                for (Isolation isolation : order) {
                    PeriodRankingContentionResult result = measureContention(contentionSeed, isolation, run, config.holdMs());
                    assertThat(result.updatedRows()).isEqualTo(1);
                    assertThat(result.sourceUpdateLatencyMs()).isGreaterThanOrEqualTo(0.0);
                    contention.add(result);
                }
            }

            PeriodRankingBenchmarkReport.ReportFiles files = PeriodRankingBenchmarkReport.write(
                config,
                List.copyOf(strategies),
                List.copyOf(chunks),
                List.copyOf(contention),
                environment(),
                Instant.now()
            );
            files.verifyWritten();
            assertThat(strategies).hasSize(config.cardinalities().size() * config.runs() * 2);
            assertThat(chunks).hasSize(config.cardinalities().size() * config.runs() * config.chunkSizes().size());
            assertThat(contention).hasSize(config.contentionRuns() * 2);
        } finally {
            cleanupBenchmarkData(config.periodDays());
        }
    }

    private void warmup(SeedSummary seed, PeriodRankingBenchmarkConfig config) {
        for (int warmup = 0; warmup < config.warmupRuns(); warmup++) {
            runStrategy("legacy", seed, config.pageSize(), 0);
            runStrategy("snapshot", seed, config.pageSize(), 0);
        }
        for (int chunkSize : config.chunkWarmupPlan()) {
            runChunk(seed, chunkSize, 0);
        }
    }

    private PeriodRankingStrategyResult runStrategy(
        String strategy,
        SeedSummary seed,
        int pageSize,
        int run
    ) {
        String runKey = runKey("strategy-" + strategy, seed.cardinality(), run);
        StrategyOutcome outcome = switch (strategy) {
            case "legacy" -> legacyGroupedPaging(runKey, seed, pageSize);
            case "snapshot" -> snapshotAndScore(runKey, seed, pageSize);
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
        Digest digest = digest(runKey);
        return new PeriodRankingStrategyResult(
            run,
            strategy,
            seed.cardinality(),
            seed.sourceRows(),
            seed.periodDays(),
            seed.activeHours(),
            pageSize,
            outcome.elapsedMs(),
            rate(seed.cardinality(), outcome.elapsedMs()),
            outcome.sourceGroupQueryCount(),
            outcome.stagingPageCount(),
            digest.count(),
            digest.sha256()
        );
    }

    private PeriodRankingChunkResult runChunk(SeedSummary seed, int chunkSize, int run) {
        String runKey = runKey("chunk-" + chunkSize, seed.cardinality(), run);
        prepareSnapshot(runKey, seed);
        ScoreOutcome outcome = scoreStaging(runKey, chunkSize);
        Digest digest = digest(runKey);
        assertThat(digest.count()).isEqualTo(seed.cardinality());
        return new PeriodRankingChunkResult(
            run,
            seed.cardinality(),
            chunkSize,
            outcome.elapsedMs(),
            rate(seed.cardinality(), outcome.elapsedMs()),
            outcome.stagingPageCount(),
            digest.sha256()
        );
    }

    private StrategyOutcome legacyGroupedPaging(String runKey, SeedSummary seed, int pageSize) {
        long startedAt = System.nanoTime();
        inTransaction(TransactionDefinition.ISOLATION_DEFAULT, () -> {
            jdbcTemplate.update("DELETE FROM product_rank_staging WHERE run_key = ?", runKey);
            return null;
        });
        Long lastProductId = null;
        int sourceGroupQueries = 0;
        int pages = 0;
        while (true) {
            Long pageStart = lastProductId;
            List<Metric> metrics = inTransaction(TransactionDefinition.ISOLATION_DEFAULT, () -> {
                List<Metric> page = pageStart == null
                    ? jdbcTemplate.query("""
                        SELECT product_id, SUM(view_count) view_count,
                               SUM(like_count) like_count, SUM(sales_count) sales_count
                        FROM product_metric_hourly
                        WHERE metric_date BETWEEN ? AND ?
                        GROUP BY product_id
                        ORDER BY product_id ASC
                        LIMIT ?
                        """, this::metric, PERIOD_START, seed.periodEnd(), pageSize)
                    : jdbcTemplate.query("""
                        SELECT *
                        FROM (
                            SELECT product_id, SUM(view_count) view_count,
                                   SUM(like_count) like_count, SUM(sales_count) sales_count
                            FROM product_metric_hourly
                            WHERE metric_date BETWEEN ? AND ?
                            GROUP BY product_id
                        ) MAIN_QRY
                        WHERE product_id > ?
                        ORDER BY product_id ASC
                        LIMIT ?
                        """, this::metric, PERIOD_START, seed.periodEnd(), pageStart, pageSize);
                if (!page.isEmpty()) {
                    insertScoredMetrics(runKey, page);
                }
                return page;
            });
            sourceGroupQueries++;
            if (metrics.isEmpty()) {
                break;
            }
            pages++;
            lastProductId = metrics.getLast().productId();
        }
        return new StrategyOutcome(elapsedMs(startedAt), sourceGroupQueries, pages);
    }

    private StrategyOutcome snapshotAndScore(String runKey, SeedSummary seed, int chunkSize) {
        long startedAt = System.nanoTime();
        prepareSnapshot(runKey, seed);
        ScoreOutcome scoreOutcome = scoreStaging(runKey, chunkSize);
        return new StrategyOutcome(elapsedMs(startedAt), 1, scoreOutcome.stagingPageCount());
    }

    private ScoreOutcome scoreStaging(String runKey, int chunkSize) {
        long startedAt = System.nanoTime();
        Long lastProductId = null;
        int pages = 0;
        while (true) {
            long pageStart = lastProductId == null ? 0L : lastProductId;
            List<Metric> metrics = inTransaction(TransactionDefinition.ISOLATION_DEFAULT, () -> {
                List<Metric> page = jdbcTemplate.query("""
                    SELECT product_id, view_count, like_count, sales_count
                    FROM product_rank_staging
                    WHERE run_key = ? AND product_id > ?
                    ORDER BY product_id ASC
                    LIMIT ?
                    """, this::metric, runKey, pageStart, chunkSize);
                if (!page.isEmpty()) {
                    updateScores(runKey, page);
                }
                return page;
            });
            if (metrics.isEmpty()) {
                break;
            }
            pages++;
            lastProductId = metrics.getLast().productId();
        }
        return new ScoreOutcome(elapsedMs(startedAt), pages);
    }

    private void prepareSnapshot(String runKey, SeedSummary seed) {
        inTransaction(TransactionDefinition.ISOLATION_READ_COMMITTED, () -> {
            jdbcTemplate.update("DELETE FROM product_rank_staging WHERE run_key = ?", runKey);
            insertSnapshot(runKey, seed);
            return null;
        });
    }

    private int insertSnapshot(String runKey, SeedSummary seed) {
        return jdbcTemplate.update("""
            INSERT INTO product_rank_staging
                (run_key, period_type, period_start, job_instance_id,
                 product_id, score, view_count, like_count, sales_count, created_at, updated_at)
            SELECT ?, 'WEEKLY', ?, 0, product_id, 0,
                   SUM(view_count), SUM(like_count), SUM(sales_count), NOW(6), NOW(6)
            FROM product_metric_hourly
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """, runKey, PERIOD_START, PERIOD_START, seed.periodEnd());
    }

    private void insertScoredMetrics(String runKey, List<Metric> metrics) {
        jdbcTemplate.batchUpdate("""
            INSERT INTO product_rank_staging
                (run_key, period_type, period_start, job_instance_id,
                 product_id, score, view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, 'WEEKLY', ?, 0, ?, ?, ?, ?, ?, NOW(6), NOW(6))
            """, metrics, metrics.size(), (statement, metric) -> {
                statement.setString(1, runKey);
                statement.setDate(2, Date.valueOf(PERIOD_START));
                statement.setLong(3, metric.productId());
                statement.setBigDecimal(4, score(metric));
                statement.setLong(5, metric.viewCount());
                statement.setLong(6, metric.likeCount());
                statement.setLong(7, metric.salesCount());
            });
    }

    private void updateScores(String runKey, List<Metric> metrics) {
        jdbcTemplate.batchUpdate("""
            UPDATE product_rank_staging
            SET score = ?, updated_at = NOW(6)
            WHERE run_key = ? AND product_id = ?
            """, metrics, metrics.size(), (statement, metric) -> {
                statement.setBigDecimal(1, score(metric));
                statement.setString(2, runKey);
                statement.setLong(3, metric.productId());
            });
    }

    private BigDecimal score(Metric metric) {
        return BigDecimal.valueOf(RankingScoreFormula.calculate(
            metric.viewCount(), metric.likeCount(), metric.salesCount()
        ));
    }

    private PeriodRankingContentionResult measureContention(
        SeedSummary seed,
        Isolation isolation,
        int run,
        long holdMs
    ) throws Exception {
        String runKey = runKey("contention-" + isolation.label(), seed.cardinality(), run);
        jdbcTemplate.update("DELETE FROM product_rank_staging WHERE run_key = ?", runKey);
        jdbcTemplate.update("""
            UPDATE product_metric_hourly
            SET view_count = 10, updated_at = NOW(6)
            WHERE metric_date = ? AND metric_hour = 0 AND product_id = 1
            """, PERIOD_START);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        AtomicReference<Double> insertMs = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(isolation.level());
        transaction.setTimeout(30);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> snapshot = executor.submit(() -> {
                transaction.executeWithoutResult(status -> {
                    long insertStartedAt = System.nanoTime();
                    insertSnapshot(runKey, seed);
                    insertMs.set(elapsedMs(insertStartedAt));
                    inserted.countDown();
                    await(updateStarted, "source UPDATE did not start");
                    sleep(holdMs);
                });
                return null;
            });
            Future<UpdateOutcome> update = executor.submit(() -> {
                await(inserted, "snapshot INSERT did not finish");
                long updateStartedAt = System.nanoTime();
                updateStarted.countDown();
                int updated = jdbcTemplate.update("""
                    UPDATE product_metric_hourly
                    SET view_count = view_count + 1, updated_at = NOW(6)
                    WHERE metric_date = ? AND metric_hour = 0 AND product_id = 1
                    """, PERIOD_START);
                return new UpdateOutcome(elapsedMs(updateStartedAt), updated);
            });
            snapshot.get(30_000 + holdMs, TimeUnit.MILLISECONDS);
            UpdateOutcome updateOutcome = update.get(30_000 + holdMs, TimeUnit.MILLISECONDS);
            return new PeriodRankingContentionResult(
                run,
                isolation.label(),
                holdMs,
                insertMs.get(),
                updateOutcome.elapsedMs(),
                updateOutcome.updatedRows()
            );
        }
    }

    private SeedSummary seed(int cardinality, int periodDays, int activeHours) {
        cleanupBenchmarkData(periodDays);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("""
                INSERT INTO product_metric_hourly
                    (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                """)) {
                int batched = 0;
                for (int day = 0; day < periodDays; day++) {
                    for (int hour = 0; hour < activeHours; hour++) {
                        for (long productId = 1; productId <= cardinality; productId++) {
                            statement.setDate(1, Date.valueOf(PERIOD_START.plusDays(day)));
                            statement.setInt(2, hour);
                            statement.setLong(3, productId);
                            statement.setLong(4, Math.floorMod(productId * 17 + day * 3L + hour, 5));
                            statement.setLong(5, 1 + Math.floorMod(productId * 31 + day * 7L + hour, 11));
                            statement.setLong(6, Math.floorMod(productId * 13 + day + hour, 3));
                            statement.addBatch();
                            if (++batched % INSERT_BATCH_SIZE == 0) {
                                statement.executeBatch();
                            }
                        }
                    }
                }
                if (batched % INSERT_BATCH_SIZE != 0) {
                    statement.executeBatch();
                }
            }
            return null;
        });
        return new SeedSummary(
            cardinality,
            (long) cardinality * periodDays * activeHours,
            periodDays,
            activeHours,
            PERIOD_START.plusDays(periodDays - 1L)
        );
    }

    private Digest digest(String runKey) {
        StringBuilder canonical = new StringBuilder();
        List<MetricWithScore> rows = jdbcTemplate.query("""
            SELECT product_id, score, view_count, like_count, sales_count
            FROM product_rank_staging
            WHERE run_key = ?
            ORDER BY product_id ASC
            """, (rs, rowNum) -> new MetricWithScore(
            rs.getLong("product_id"),
            rs.getBigDecimal("score"),
            rs.getLong("view_count"),
            rs.getLong("like_count"),
            rs.getLong("sales_count")
        ), runKey);
        for (MetricWithScore row : rows) {
            canonical.append(row.productId()).append(':')
                .append(row.score().stripTrailingZeros().toPlainString()).append(':')
                .append(row.viewCount()).append(':').append(row.likeCount()).append(':')
                .append(row.salesCount()).append('\n');
        }
        return new Digest(rows.size(), sha256(canonical.toString()));
    }

    private Metric metric(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Metric(
            rs.getLong("product_id"),
            rs.getLong("view_count"),
            rs.getLong("like_count"),
            rs.getLong("sales_count")
        );
    }

    private PeriodRankingBenchmarkReport.Environment environment() {
        String database = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        return new PeriodRankingBenchmarkReport.Environment(
            System.getProperty("java.version"),
            System.getProperty("os.name") + " " + System.getProperty("os.arch"),
            "MySQL " + database
        );
    }

    private void cleanupBenchmarkData(int periodDays) {
        jdbcTemplate.update("DELETE FROM product_rank_staging WHERE run_key LIKE ?", RUN_KEY_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM product_metric_hourly WHERE metric_date BETWEEN ? AND ?",
            PERIOD_START,
            PERIOD_START.plusDays(Math.max(periodDays, 31) - 1L)
        );
    }

    private String runKey(String experiment, int cardinality, int run) {
        return RUN_KEY_PREFIX + experiment + ":" + cardinality + ":" + run;
    }

    private <T> T inTransaction(int isolationLevel, Supplier<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(isolationLevel);
        return transaction.execute(status -> callback.get());
    }

    private static <T> List<T> rotated(List<T> values, int offset) {
        List<T> rotated = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            rotated.add(values.get((index + offset) % values.size()));
        }
        return rotated;
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("benchmark hold interrupted", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private static double rate(long count, double elapsedMs) {
        return elapsedMs == 0.0 ? 0.0 : count / (elapsedMs / 1_000.0);
    }

    private enum Isolation {
        REPEATABLE_READ(TransactionDefinition.ISOLATION_REPEATABLE_READ, "REPEATABLE_READ"),
        READ_COMMITTED(TransactionDefinition.ISOLATION_READ_COMMITTED, "READ_COMMITTED");

        private final int level;
        private final String label;

        Isolation(int level, String label) {
            this.level = level;
            this.label = label;
        }

        int level() {
            return level;
        }

        String label() {
            return label;
        }
    }

    private record SeedSummary(
        int cardinality,
        long sourceRows,
        int periodDays,
        int activeHours,
        LocalDate periodEnd
    ) {
    }

    private record StrategyOutcome(double elapsedMs, int sourceGroupQueryCount, int stagingPageCount) {
    }

    private record ScoreOutcome(double elapsedMs, int stagingPageCount) {
    }

    private record Metric(long productId, long viewCount, long likeCount, long salesCount) {
    }

    private record MetricWithScore(
        long productId,
        BigDecimal score,
        long viewCount,
        long likeCount,
        long salesCount
    ) {
    }

    private record Digest(long count, String sha256) {
    }

    private record UpdateOutcome(double elapsedMs, int updatedRows) {
    }
}
