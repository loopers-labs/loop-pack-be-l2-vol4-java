package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CouponIssueBenchmarkIntegrationTest {

    private static final String BENCHMARK_GROUP_ID = "coupon-issue-benchmark-" + System.currentTimeMillis();
    private static final int REQUEST_COUNT = intSetting(
        "coupon.issue.benchmark.requests", "COUPON_ISSUE_BENCHMARK_REQUESTS", 10_000);
    private static final int COUPON_QUANTITY = intSetting(
        "coupon.issue.benchmark.quantity", "COUPON_ISSUE_BENCHMARK_QUANTITY", 100);
    private static final int CONCURRENCY = intSetting(
        "coupon.issue.benchmark.concurrency", "COUPON_ISSUE_BENCHMARK_CONCURRENCY", 200);
    private static final Duration DB_INSERT_TIMEOUT = Duration.ofMinutes(5);

    @Container
    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        String port = String.valueOf(redisContainer.getMappedPort(6379));
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
        registry.add("commerce-events.consumer-groups.coupon-issue", () -> BENCHMARK_GROUP_ID);
        registry.add("coupon.issue.processor.metrics-enabled", () -> "true");
    }

    @Autowired private CouponApplicationService couponApplicationService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;
    @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("10,000 concurrent coupon issue benchmark")
    @Test
    void benchmarkTenThousandConcurrentIssueRequests() throws Exception {
        Assumptions.assumeTrue(isBenchmarkEnabled(),
            "Run with -Dcoupon.issue.benchmark=true or COUPON_ISSUE_BENCHMARK=true");

        CouponModel coupon = couponRepository.save(new CouponModel(
            "benchmark coupon", CouponType.FIXED, 1_000, null,
            ZonedDateTime.now().plusDays(1), COUPON_QUANTITY));

        waitUntilCouponIssueListenerAssigned();

        int expectedIssuedCount = Math.min(REQUEST_COUNT, COUPON_QUANTITY);
        long[] latenciesNanos = new long[REQUEST_COUNT];
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(REQUEST_COUNT);

        long benchmarkStartedAt = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        for (int i = 0; i < REQUEST_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                long startedAt = 0L;
                try {
                    startLatch.await();
                    startedAt = System.nanoTime();
                    couponApplicationService.requestIssue((long) index + 1, coupon.getId());
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    failureCount.incrementAndGet();
                    if (!isExpectedSoldOut(e)) {
                        firstFailure.compareAndSet(null, e);
                    }
                } finally {
                    if (startedAt != 0L) {
                        latenciesNanos[index] = System.nanoTime() - startedAt;
                    }
                    doneLatch.countDown();
                }
            });
        }

        long requestsStartedAt = System.nanoTime();
        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.MINUTES)).isTrue();
        long apiCompletedAt = System.nanoTime();
        executor.shutdown();

        long dbCompletedAt = waitUntilUserCouponsInserted(coupon.getId(), expectedIssuedCount, DB_INSERT_TIMEOUT);

        Arrays.sort(latenciesNanos);
        BenchmarkResult result = new BenchmarkResult(
            successCount.get(),
            failureCount.get(),
            nanosToMillis(apiCompletedAt - requestsStartedAt),
            nanosToMillis(dbCompletedAt - requestsStartedAt),
            nanosToMillis(dbCompletedAt - apiCompletedAt),
            nanosToMillis(percentile(latenciesNanos, 0.50)),
            nanosToMillis(percentile(latenciesNanos, 0.95)),
            nanosToMillis(percentile(latenciesNanos, 0.99)),
            nanosToMillis(latenciesNanos[latenciesNanos.length - 1]),
            countRows("coupon_issue_requests", coupon.getId()),
            countRows("user_coupons", coupon.getId())
        );
        System.out.println(result.format());

        assertThat(firstFailure.get()).isNull();
        assertThat(result.successCount()).isEqualTo(expectedIssuedCount);
        assertThat(result.failureCount()).isEqualTo(REQUEST_COUNT - expectedIssuedCount);
        assertThat(result.userCouponRows()).isEqualTo(expectedIssuedCount);
        assertThat(result.requestRows()).isEqualTo(expectedIssuedCount);
        assertThat(nanosToMillis(System.nanoTime() - benchmarkStartedAt)).isLessThan(360_000L);
    }

    private long waitUntilUserCouponsInserted(Long couponId, int expectedCount, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (countRows("user_coupons", couponId) >= expectedCount) {
                return System.nanoTime();
            }
            Thread.sleep(100);
        }
        return System.nanoTime();
    }

    private void waitUntilCouponIssueListenerAssigned() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            int assignedPartitions = kafkaListenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> BENCHMARK_GROUP_ID.equals(container.getGroupId()))
                .mapToInt(this::assignedPartitionCount)
                .sum();
            if (assignedPartitions > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Coupon issue Kafka listener was not assigned before benchmark start.");
    }

    private int assignedPartitionCount(MessageListenerContainer container) {
        return container.getAssignedPartitions() == null ? 0 : container.getAssignedPartitions().size();
    }

    private long countRows(String tableName, Long couponId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE coupon_id = ?",
            Long.class,
            couponId
        );
        return count == null ? 0L : count;
    }

    private long percentile(long[] sortedValues, double percentile) {
        int index = (int) Math.ceil(sortedValues.length * percentile) - 1;
        return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
    }

    private long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private boolean isBenchmarkEnabled() {
        return Boolean.getBoolean("coupon.issue.benchmark")
            || "true".equalsIgnoreCase(System.getenv("COUPON_ISSUE_BENCHMARK"));
    }

    private boolean isExpectedSoldOut(Throwable e) {
        return e instanceof CoreException coreException
            && coreException.getErrorType() == ErrorType.CONFLICT;
    }

    private static int intSetting(String propertyName, String environmentName, int defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Integer.parseInt(propertyValue);
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Integer.parseInt(environmentValue);
        }
        return defaultValue;
    }

    private record BenchmarkResult(
        int successCount,
        int failureCount,
        long apiTotalMillis,
        long dbCompleteTotalMillis,
        long dbCatchUpAfterApiMillis,
        long apiP50Millis,
        long apiP95Millis,
        long apiP99Millis,
        long apiMaxMillis,
        long requestRows,
        long userCouponRows
    ) {
        String format() {
            return """
                
                ========== Coupon Issue Benchmark ==========
                requests                 : %d
                coupon quantity          : %d
                success                  : %d
                failure                  : %d
                api total                : %d ms
                db complete total        : %d ms
                db catch-up after api    : %d ms
                api latency p50          : %d ms
                api latency p95          : %d ms
                api latency p99          : %d ms
                api latency max          : %d ms
                coupon_issue_requests    : %d rows
                user_coupons             : %d rows
                ===========================================
                """.formatted(
                REQUEST_COUNT,
                COUPON_QUANTITY,
                successCount,
                failureCount,
                apiTotalMillis,
                dbCompleteTotalMillis,
                dbCatchUpAfterApiMillis,
                apiP50Millis,
                apiP95Millis,
                apiP99Millis,
                apiMaxMillis,
                requestRows,
                userCouponRows
            );
        }
    }
}
