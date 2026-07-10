package com.loopers.benchmark.queue;

import com.loopers.application.queue.WaitingQueueAdmitService;
import com.loopers.application.queue.WaitingQueueProperties;
import com.loopers.application.queue.WaitingQueueRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.PasswordHasher;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("benchmark")
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.show-sql=false",
        "loopers.waiting-queue.scheduler-enabled=false",
        "loopers.payment.reconciliation-enabled=false",
        "management.server.port=0"
    }
)
class WaitingQueueCapacityBenchmarkTest {

    private static final String ORDER_ENDPOINT = "http://127.0.0.1:%d/api/v1/orders";
    private static final String BENCHMARK_PASSWORD = "abc123!?";
    private static final String PRECOMPUTED_PASSWORD_HASH =
        "$2y$10$m7nFO9NjV8Fb/2Tj43vAu.TtQ.GgNmjFDo.KYrsFpoN.JkawOfFZu";
    private static final Duration ENTRY_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SCENARIO_TIMEOUT_MARGIN = Duration.ofSeconds(30);
    private static final long HIKARI_SAMPLE_INTERVAL_MS = 5L;

    @LocalServerPort
    private int serverPort;

    private final WaitingQueueRepository waitingQueueRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final PasswordHasher passwordHasher;
    private final JdbcTemplate jdbcTemplate;
    private final HikariDataSource hikariDataSource;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final HttpClient httpClient;

    @Autowired
    WaitingQueueCapacityBenchmarkTest(
        WaitingQueueRepository waitingQueueRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        OutboxEventJpaRepository outboxEventJpaRepository,
        PasswordHasher passwordHasher,
        JdbcTemplate jdbcTemplate,
        HikariDataSource hikariDataSource,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.passwordHasher = passwordHasher;
        this.jdbcTemplate = jdbcTemplate;
        this.hikariDataSource = hikariDataSource;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    @DisplayName("설정한 배치와 지연 조합으로 실제 대기열 주문 용량을 측정하고 보고서를 남긴다.")
    @Test
    void benchmarksWaitingQueueCapacityAndWritesReports() throws Exception {
        // arrange
        WaitingQueueBenchmarkConfig config = WaitingQueueBenchmarkConfig.fromSystemProperties();
        validateHarness(config);
        List<WaitingQueueBenchmarkScenario> measuredScenarios = new ArrayList<>();
        int scenarioSequence = 1;

        // act
        if (config.warmupUsers() > 0) {
            runScenario(
                config,
                config.batchSizes().get(0),
                config.admitDelaysMs().get(0),
                0,
                config.warmupUsers(),
                scenarioSequence++
            );
        }
        for (int batchSize : config.batchSizes()) {
            for (long admitDelayMs : config.admitDelaysMs()) {
                for (int run = 1; run <= config.runs(); run++) {
                    measuredScenarios.add(runScenario(
                        config,
                        batchSize,
                        admitDelayMs,
                        run,
                        config.users(),
                        scenarioSequence++
                    ));
                }
            }
        }
        if (measuredScenarios.size() != config.expectedScenarioCount()) {
            throw new IllegalStateException("Not all configured benchmark scenarios completed");
        }
        WaitingQueueBenchmarkReport.ReportFiles reportFiles = WaitingQueueBenchmarkReport.write(
            config,
            WaitingQueueBenchmarkEnvironment.capture(serverPort),
            measuredScenarios,
            Instant.now()
        );
        cleanState();

        // assert
        reportFiles.verifyWritten();
    }

    private WaitingQueueBenchmarkScenario runScenario(
        WaitingQueueBenchmarkConfig config,
        int batchSize,
        long admitDelayMs,
        int run,
        int users,
        int scenarioSequence
    ) throws Exception {
        cleanState();
        List<BenchmarkUser> benchmarkUsers = benchmarkUsers(users, scenarioSequence);
        Long productId = seedScenario(benchmarkUsers);
        WaitingQueueAdmitService admitService = admitService(batchSize, admitDelayMs);
        int workerCount = Math.min(config.concurrency(), users);
        long totalStartedAt = System.nanoTime();
        long deadline = scenarioDeadline(totalStartedAt, users, workerCount, batchSize, admitDelayMs);
        ExecutorService requestExecutor = Executors.newFixedThreadPool(
            workerCount,
            namedThreadFactory("queue-benchmark-request-")
        );
        List<Future<WaitingQueueBenchmarkRequest>> requestTasks = new ArrayList<>(users);
        HikariPoolSampler poolSampler = new HikariPoolSampler(hikariDataSource.getHikariPoolMXBean());
        boolean allTasksAwaited = false;

        List<WaitingQueueBenchmarkRequest> requests;
        double queueDrainDurationMs;
        double e2eDurationMs;
        try {
            poolSampler.start();
            enqueueDeterministically(benchmarkUsers);
            long e2eStartedAt = System.nanoTime();
            queueDrainDurationMs = admitAndSubmitOrders(
                benchmarkUsers,
                productId,
                admitService,
                batchSize,
                admitDelayMs,
                e2eStartedAt,
                deadline,
                requestExecutor,
                requestTasks
            );
            requests = awaitRequests(requestTasks, deadline);
            allTasksAwaited = true;
            e2eDurationMs = elapsedMs(e2eStartedAt, System.nanoTime());
        } finally {
            try {
                shutdownExecutor(requestExecutor, requestTasks, allTasksAwaited);
            } finally {
                poolSampler.close();
            }
        }

        long remainingQueue = waitingQueueRepository.countWaitingUsers();
        long remainingTokens = benchmarkUsers.stream()
            .filter(user -> waitingQueueRepository.findEntryToken(user.loginId()).isPresent())
            .count();
        long persistedOrders = orderJpaRepository.count();
        long persistedOutboxEvents = outboxEventJpaRepository.count();
        double totalDurationMs = elapsedMs(totalStartedAt, System.nanoTime());

        return WaitingQueueBenchmarkScenario.completed(
            batchSize,
            admitDelayMs,
            run,
            users,
            config.concurrency(),
            config.dbPoolSize(),
            hikariDataSource.getMaximumPoolSize(),
            hikariDataSource.getMinimumIdle(),
            totalDurationMs,
            e2eDurationMs,
            queueDrainDurationMs,
            remainingQueue,
            remainingTokens,
            persistedOrders,
            persistedOutboxEvents,
            poolSampler.maxActive(),
            poolSampler.maxPending(),
            requests
        );
    }

    private Long seedScenario(List<BenchmarkUser> users) {
        BrandJpaEntity brand = brandJpaRepository.saveAndFlush(
            BrandJpaEntity.from(new Brand("Queue Benchmark", "Waiting queue capacity benchmark"))
        );
        ProductJpaEntity product = productJpaRepository.saveAndFlush(
            ProductJpaEntity.from(new Product(
                brand.getId(),
                "Shared hot-row product",
                "One high-stock product shared by every benchmark order",
                10_000L,
                Integer.MAX_VALUE
            ))
        );
        jdbcTemplate.batchUpdate(
            """
                INSERT INTO users
                    (login_id, password_hash, name, birth, email, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL)
                """,
            users,
            Math.min(users.size(), 1_000),
            (statement, user) -> {
                statement.setString(1, user.loginId());
                statement.setString(2, PRECOMPUTED_PASSWORD_HASH);
                statement.setString(3, "Benchmark User");
                statement.setDate(4, Date.valueOf(LocalDate.of(1990, 1, 1)));
                statement.setString(5, user.loginId() + "@benchmark.local");
            }
        );
        return product.getId();
    }

    private WaitingQueueAdmitService admitService(int batchSize, long admitDelayMs) {
        WaitingQueueProperties properties = new WaitingQueueProperties(
            false,
            Duration.ofMillis(admitDelayMs),
            batchSize,
            ENTRY_TOKEN_TTL,
            null
        );
        return new WaitingQueueAdmitService(waitingQueueRepository, properties);
    }

    private void enqueueDeterministically(List<BenchmarkUser> users) {
        for (int index = 0; index < users.size(); index++) {
            BenchmarkUser user = users.get(index);
            if (!waitingQueueRepository.enqueueIfAbsent(user.loginId(), index)) {
                throw new IllegalStateException("Failed to enqueue unique benchmark user: " + user.loginId());
            }
        }
        long waitingUsers = waitingQueueRepository.countWaitingUsers();
        if (waitingUsers != users.size()) {
            throw new IllegalStateException(
                "Expected " + users.size() + " queued users but found " + waitingUsers
            );
        }
    }

    private double admitAndSubmitOrders(
        List<BenchmarkUser> users,
        Long productId,
        WaitingQueueAdmitService admitService,
        int batchSize,
        long admitDelayMs,
        long e2eStartedAt,
        long deadline,
        ExecutorService requestExecutor,
        List<Future<WaitingQueueBenchmarkRequest>> requestTasks
    ) throws InterruptedException {
        int admittedOffset = 0;
        long drainedAt = 0L;
        while (admittedOffset < users.size()) {
            if (admittedOffset > 0) {
                sleepWithinDeadline(admitDelayMs, deadline);
            }
            int expectedAdmitted = Math.min(batchSize, users.size() - admittedOffset);
            int admitted = admitService.admit();
            if (admitted != expectedAdmitted) {
                throw new IllegalStateException(
                    "Expected to admit " + expectedAdmitted + " users but admitted " + admitted
                );
            }
            for (int index = admittedOffset; index < admittedOffset + admitted; index++) {
                BenchmarkUser user = users.get(index);
                String entryToken = waitingQueueRepository.findEntryToken(user.loginId())
                    .orElseThrow(() -> new IllegalStateException(
                        "Admitted user has no entry token: " + user.loginId()
                    ));
                requestTasks.add(requestExecutor.submit(() -> submitOrder(user, entryToken, productId)));
            }
            admittedOffset += admitted;
            if (waitingQueueRepository.countWaitingUsers() == 0) {
                drainedAt = System.nanoTime();
            }
        }
        if (drainedAt == 0L) {
            throw new IllegalStateException("Waiting queue did not drain");
        }
        return elapsedMs(e2eStartedAt, drainedAt);
    }

    private WaitingQueueBenchmarkRequest submitOrder(BenchmarkUser user, String entryToken, Long productId) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ORDER_ENDPOINT.formatted(serverPort)))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Loopers-LoginId", user.loginId())
                .header("X-Loopers-LoginPw", BENCHMARK_PASSWORD)
                .header("X-Entry-Token", entryToken)
                .POST(HttpRequest.BodyPublishers.ofString(orderRequestBody(productId)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new WaitingQueueBenchmarkRequest(
                user.loginId(),
                response.statusCode(),
                success,
                elapsedMs(startedAt, System.nanoTime()),
                success ? "" : httpFailure(response)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedRequest(user, startedAt, exception);
        } catch (Exception exception) {
            return failedRequest(user, startedAt, exception);
        }
    }

    private List<WaitingQueueBenchmarkRequest> awaitRequests(
        List<Future<WaitingQueueBenchmarkRequest>> tasks,
        long deadline
    ) throws Exception {
        List<WaitingQueueBenchmarkRequest> requests = new ArrayList<>(tasks.size());
        for (Future<WaitingQueueBenchmarkRequest> task : tasks) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new TimeoutException("Benchmark scenario exceeded its bounded execution time");
            }
            requests.add(task.get(remainingNanos, TimeUnit.NANOSECONDS));
        }
        return List.copyOf(requests);
    }

    private void shutdownExecutor(
        ExecutorService executor,
        List<? extends Future<?>> tasks,
        boolean allTasksAwaited
    ) throws InterruptedException {
        if (allTasksAwaited) {
            executor.shutdown();
        } else {
            tasks.forEach(task -> task.cancel(true));
            executor.shutdownNow();
        }
        if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Benchmark request executor did not terminate");
            }
        }
    }

    private void sleepWithinDeadline(long delayMs, long deadline) throws InterruptedException {
        long remainingNanos = deadline - System.nanoTime();
        long delayNanos;
        try {
            delayNanos = Math.multiplyExact(delayMs, 1_000_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Admission delay is too large", exception);
        }
        if (remainingNanos <= delayNanos) {
            throw new IllegalStateException("Configured admission pacing exceeded the scenario deadline");
        }
        TimeUnit.MILLISECONDS.sleep(delayMs);
    }

    private long scenarioDeadline(
        long startedAt,
        int users,
        int concurrency,
        int batchSize,
        long admitDelayMs
    ) {
        try {
            long admissionIntervals = Math.max(0L, (users + (long) batchSize - 1L) / batchSize - 1L);
            long admissionBudgetMs = Math.multiplyExact(admissionIntervals, admitDelayMs);
            long requestWaves = (users + (long) concurrency - 1L) / concurrency;
            long requestBudgetMs = Math.multiplyExact(requestWaves, HTTP_REQUEST_TIMEOUT.toMillis());
            long timeoutMs = Math.addExact(
                Math.addExact(admissionBudgetMs, requestBudgetMs),
                SCENARIO_TIMEOUT_MARGIN.toMillis()
            );
            return Math.addExact(startedAt, Math.multiplyExact(timeoutMs, 1_000_000L));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Benchmark configuration creates an unbounded scenario duration", exception);
        }
    }

    private List<BenchmarkUser> benchmarkUsers(int users, int scenarioSequence) {
        long firstUserNumber = scenarioSequence * 100_000L;
        return java.util.stream.IntStream.range(0, users)
            .mapToObj(index -> new BenchmarkUser(String.format(
                Locale.ROOT,
                "qb%08d",
                firstUserNumber + index
            )))
            .toList();
    }

    private void validateHarness(WaitingQueueBenchmarkConfig config) {
        if (!passwordHasher.matches(BENCHMARK_PASSWORD, PRECOMPUTED_PASSWORD_HASH)) {
            throw new IllegalStateException("Precomputed benchmark password hash does not match the benchmark password");
        }
        if (hikariDataSource.getMaximumPoolSize() != config.dbPoolSize()) {
            throw new IllegalStateException(
                "queueBenchmarkDbPoolSize=" + config.dbPoolSize()
                    + " was not bound; effective Hikari maximum is " + hikariDataSource.getMaximumPoolSize()
            );
        }
        if (hikariDataSource.getHikariPoolMXBean() == null) {
            throw new IllegalStateException("Hikari pool metrics are unavailable");
        }
    }

    private void cleanState() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String orderRequestBody(Long productId) {
        return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],\"couponId\":null}";
    }

    private String httpFailure(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ").trim();
        if (body.length() > 200) {
            body = body.substring(0, 200);
        }
        return body.isEmpty() ? "HTTP " + response.statusCode() : "HTTP " + response.statusCode() + ": " + body;
    }

    private WaitingQueueBenchmarkRequest failedRequest(BenchmarkUser user, long startedAt, Exception exception) {
        String message = exception.getMessage() == null ? "" : ": " + exception.getMessage();
        return new WaitingQueueBenchmarkRequest(
            user.loginId(),
            0,
            false,
            elapsedMs(startedAt, System.nanoTime()),
            exception.getClass().getSimpleName() + message
        );
    }

    private static java.util.concurrent.ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static double elapsedMs(long startedAt, long endedAt) {
        return (endedAt - startedAt) / 1_000_000.0;
    }

    private record BenchmarkUser(String loginId) {
    }

    private static final class HikariPoolSampler implements AutoCloseable {
        private final HikariPoolMXBean pool;
        private final AtomicInteger maxActive = new AtomicInteger();
        private final AtomicInteger maxPending = new AtomicInteger();
        private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("queue-benchmark-hikari-")
        );

        private HikariPoolSampler(HikariPoolMXBean pool) {
            this.pool = pool;
        }

        private void start() {
            sample();
            sampler.scheduleAtFixedRate(
                this::sample,
                0L,
                HIKARI_SAMPLE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        }

        private void sample() {
            maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
            maxPending.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
        }

        private int maxActive() {
            return maxActive.get();
        }

        private int maxPending() {
            return maxPending.get();
        }

        @Override
        public void close() throws InterruptedException {
            sample();
            sampler.shutdownNow();
            if (!sampler.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Hikari sampler executor did not terminate");
            }
        }
    }
}
