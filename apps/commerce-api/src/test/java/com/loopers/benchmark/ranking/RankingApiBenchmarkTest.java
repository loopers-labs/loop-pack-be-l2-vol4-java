package com.loopers.benchmark.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

@Tag("benchmark")
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "loopers.waiting-queue.scheduler-enabled=false",
        "loopers.outbox.relay-enabled=false",
        "loopers.payment.reconciliation-enabled=false",
        "management.server.port=0"
    }
)
class RankingApiBenchmarkTest {
    private static final LocalDate BENCHMARK_DATE = LocalDate.of(2026, 7, 16);
    private static final long BRAND_ID = 9_000_001L;
    private static final long FIRST_PRODUCT_ID = 10_000_001L;

    @LocalServerPort
    private int serverPort;

    private final RankingRepository rankingRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    RankingApiBenchmarkTest(
        RankingRepository rankingRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        ObjectMapper objectMapper
    ) {
        this.rankingRepository = rankingRepository;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @DisplayName("ZSET 크기별 Redis 조회와 전체 Ranking HTTP 응답 성능을 측정한다.")
    @Test
    void benchmarksRedisRepositoryAndFullHttpPath() throws Exception {
        RankingBenchmarkConfig config = RankingBenchmarkConfig.fromSystemProperties();
        cleanState();
        seedProducts(config.pageSizes().stream().mapToInt(Integer::intValue).max().orElseThrow());
        List<RankingBenchmarkResult> results = new ArrayList<>();

        try {
            for (int cardinality : config.cardinalities()) {
                seedRanking(cardinality);
                long memoryBytes = redisMemoryUsage(DailyRankingKey.from(BENCHMARK_DATE));
                double bytesPerMember = (double) memoryBytes / cardinality;
                for (int pageSize : config.pageSizes()) {
                    verifyScenario(cardinality, pageSize);
                    warmup(config, pageSize);
                    for (int run = 1; run <= config.runs(); run++) {
                        results.add(measure("redis-repository", cardinality, pageSize, run, config, memoryBytes,
                            bytesPerMember, () -> rankingRepository.findRankedProducts(BENCHMARK_DATE, 1, pageSize).size() == pageSize));
                        results.add(measure("full-http", cardinality, pageSize, run, config, memoryBytes,
                            bytesPerMember, () -> requestRanking(pageSize)));
                    }
                }
            }
            RankingBenchmarkReport.write(
                config,
                RankingBenchmarkEnvironment.capture(redisVersion()),
                results,
                Instant.now()
            ).verifyWritten();
        } finally {
            cleanState();
        }
    }

    private void warmup(RankingBenchmarkConfig config, int pageSize) throws Exception {
        for (int index = 0; index < config.warmup(); index++) {
            rankingRepository.findRankedProducts(BENCHMARK_DATE, 1, pageSize);
            requestRanking(pageSize);
        }
    }

    private RankingBenchmarkResult measure(
        String boundary,
        int cardinality,
        int pageSize,
        int run,
        RankingBenchmarkConfig config,
        long memoryBytes,
        double bytesPerMember,
        CheckedOperation operation
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(config.concurrency(), config.iterations()));
        List<Callable<Measurement>> tasks = new ArrayList<>(config.iterations());
        for (int index = 0; index < config.iterations(); index++) {
            tasks.add(() -> {
                long startedAt = System.nanoTime();
                try {
                    return new Measurement(operation.execute(), elapsedMs(startedAt));
                } catch (Exception exception) {
                    return new Measurement(false, elapsedMs(startedAt));
                }
            });
        }

        long scenarioStartedAt = System.nanoTime();
        List<Future<Measurement>> futures;
        try {
            futures = executor.invokeAll(tasks, 5, TimeUnit.MINUTES);
        } finally {
            executor.shutdownNow();
        }
        double elapsedMs = elapsedMs(scenarioStartedAt);
        List<Measurement> measurements = new ArrayList<>(futures.size());
        for (Future<Measurement> future : futures) {
            if (!future.isCancelled()) measurements.add(future.get());
        }
        List<Double> latencies = measurements.stream().map(Measurement::latencyMs).toList();
        int successes = (int) measurements.stream().filter(Measurement::success).count();
        int failures = config.iterations() - successes;
        if (latencies.isEmpty()) throw new IllegalStateException("No ranking benchmark measurements completed");
        return new RankingBenchmarkResult(
            boundary, cardinality, pageSize, run, config.iterations(), successes, failures, elapsedMs,
            RankingBenchmarkStatistics.summarize(latencies, elapsedMs), memoryBytes, bytesPerMember
        );
    }

    private boolean requestRanking(int pageSize) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + serverPort
            + "/api/v1/rankings?date=20260716&page=1&size=" + pageSize);
        HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    private void verifyScenario(int cardinality, int pageSize) throws Exception {
        var repositoryResult = rankingRepository.findRankedProducts(BENCHMARK_DATE, 1, pageSize);
        if (repositoryResult.size() != pageSize
            || repositoryResult.get(0).rank() != 1L
            || !repositoryResult.get(0).productId().equals(FIRST_PRODUCT_ID)) {
            throw new IllegalStateException("Redis ranking seed verification failed");
        }
        URI uri = URI.create("http://127.0.0.1:" + serverPort
            + "/api/v1/rankings?date=20260716&page=1&size=" + pageSize);
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        JsonNode data = objectMapper.readTree(response.body()).path("data");
        JsonNode first = data.path(0);
        if (response.statusCode() != 200
            || data.size() != pageSize
            || first.path("rank").asLong() != 1L
            || first.path("product").path("id").asLong() != FIRST_PRODUCT_ID
            || first.path("product").path("brand").path("id").asLong() != BRAND_ID
            || cardinality < pageSize) {
            throw new IllegalStateException("Full HTTP ranking seed verification failed");
        }
    }

    private void seedProducts(int cardinality) {
        jdbcTemplate.update(
            "INSERT INTO brand (id, name, description, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            BRAND_ID, "Ranking benchmark brand", "benchmark"
        );
        List<Object[]> products = new ArrayList<>(cardinality);
        for (int index = 0; index < cardinality; index++) {
            products.add(new Object[]{FIRST_PRODUCT_ID + index, BRAND_ID, "benchmark-product-" + index});
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO product (id, brand_id, name, description, price, stock, like_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'benchmark', 10000, 100, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            products
        );
    }

    private void seedRanking(int cardinality) {
        String key = DailyRankingKey.from(BENCHMARK_DATE);
        redisTemplate.delete(key);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (int index = 0; index < cardinality; index++) {
                byte[] member = Long.toString(FIRST_PRODUCT_ID + index).getBytes(StandardCharsets.UTF_8);
                connection.zSetCommands().zAdd(keyBytes, cardinality - index, member);
            }
            return null;
        });
    }

    private long redisMemoryUsage(String key) {
        Number result = redisTemplate.execute((RedisCallback<Number>) connection ->
            awaitMemoryUsage(nativeCommands(connection.getNativeConnection()), key)
        );
        if (result == null) throw new IllegalStateException("Redis MEMORY USAGE returned null for " + key);
        return result.longValue();
    }

    private String redisVersion() {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            var serverInfo = connection.serverCommands().info("server");
            return serverInfo == null ? "unknown" : serverInfo.getProperty("redis_version", "unknown");
        });
    }

    private Long awaitMemoryUsage(RedisAsyncCommands<byte[], byte[]> commands, String key) {
        try {
            return commands.memoryUsage(key.getBytes(StandardCharsets.UTF_8)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading Redis memory usage", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to read Redis memory usage", exception.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private RedisAsyncCommands<byte[], byte[]> nativeCommands(Object connection) {
        return (RedisAsyncCommands<byte[], byte[]>) connection;
    }

    private void cleanState() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private record Measurement(boolean success, double latencyMs) {
    }

    @FunctionalInterface
    private interface CheckedOperation {
        boolean execute() throws Exception;
    }
}
