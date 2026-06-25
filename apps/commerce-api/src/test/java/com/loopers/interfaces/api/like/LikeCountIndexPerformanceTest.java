package com.loopers.interfaces.api.like;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * like_count 인덱스 비용 성능 테스트 — 실행 중인 로컬 서버 대상
 *
 * 사전 조건:
 *   1. docker-compose -f ./docker/infra-compose.yml up -d
 *   2. docker exec -i docker-mysql-1 mysql -u application -papplication loopers < docker/seed-data.sql
 *   3. ./gradlew :apps:commerce-api:bootRun
 *
 * 실행:
 *   ./gradlew :apps:commerce-api:test -Dgroups="performance"
 *
 * 비교 방법:
 *   1. 인덱스 있는 상태에서 실행 → 결과 기록
 *   2. 인덱스 제거:
 *      ALTER TABLE product DROP INDEX idx_product_like_count;
 *      ALTER TABLE product DROP INDEX idx_product_brand_like_count;
 *   3. 동일 테스트 재실행 → 결과 비교
 */
@Tag("performance")
class LikeCountIndexPerformanceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LIST_ENDPOINT = BASE_URL + "/api/v1/products?sort=like_desc&page=0&size=20";

    private static final int USER_COUNT = 100;   // seed 유저 seeduser_1 ~ seeduser_100 사용
    private static final String PASSWORD = "Test1234!";
    private static final String SEED_USER_PREFIX = "seeduser_";

    private static final int WARMUP_COUNT = 3;
    private static final int READ_MEASURE_COUNT = 20;

    private final RestTemplate restTemplate = new RestTemplate();
    private ExecutorService executor;

    // 테스트용 유저 ID 목록 (setup에서 생성)
    private final List<String> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(USER_COUNT);
        createTestUsers();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────────
    // 1. 쓰기 단독 — 분산 상품 (경합 없음)
    // ─────────────────────────────────────────────

    @DisplayName("[성능] like_count 쓰기 - 분산 상품 (인덱스 비용 측정)")
    @Test
    void write_distributed_products() throws InterruptedException {
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < USER_COUNT; i++) {
            final String userId = userIds.get(i);
            final String productId = String.valueOf(i + 1L);  // 서로 다른 상품
            futures.add(executor.submit(() -> {
                long start = System.nanoTime();
                like(userId, productId);
                return elapsed(start);
            }));
        }

        printWriteReport("쓰기 분산 (상품 각각, 경합 없음)", collect(futures));
    }

    // ─────────────────────────────────────────────
    // 2. 쓰기 단독 — 핫스팟 집중 (인덱스 페이지 경합)
    // ─────────────────────────────────────────────

    @DisplayName("[성능] like_count 쓰기 - 핫스팟 단일 상품 (인덱스 경합 측정)")
    @Test
    void write_hotspot_single_product() throws InterruptedException {
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < USER_COUNT; i++) {
            final String userId = userIds.get(i);
            futures.add(executor.submit(() -> {
                long start = System.nanoTime();
                like(userId, "1");  // 동일 상품에 집중
                return elapsed(start);
            }));
        }

        printWriteReport("쓰기 핫스팟 (상품 1개 집중, 인덱스 경합)", collect(futures));
    }

    // ─────────────────────────────────────────────
    // 3. 읽기-쓰기 혼합 (쓰기 부하 중 읽기 latency)
    // ─────────────────────────────────────────────

    @DisplayName("[성능] like_desc 조회 - 쓰기 부하 없을 때 baseline")
    @Test
    void read_baseline() {
        for (int i = 0; i < WARMUP_COUNT; i++) {
            readList();
        }

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < READ_MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            readList();
            durations.add(elapsed(start));
        }

        printReadReport("like_desc 조회 (쓰기 부하 없음)", durations);
    }

    @DisplayName("[성능] like_desc 조회 - 분산 쓰기 부하 중 읽기 latency")
    @Test
    void read_under_distributed_write_pressure() throws InterruptedException {
        // 백그라운드: 분산 쓰기 부하 지속
        for (int i = 0; i < USER_COUNT; i++) {
            final String userId = userIds.get(i);
            final int base = i * 10;
            executor.submit(() -> {
                for (int j = 0; j < 10; j++) {
                    like(userId, String.valueOf(base + j + 1));
                }
            });
        }

        // 전경: 읽기 latency 측정
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < READ_MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            readList();
            durations.add(elapsed(start));
        }

        printReadReport("like_desc 조회 (분산 쓰기 부하 중)", durations);
    }

    @DisplayName("[성능] like_desc 조회 - 핫스팟 쓰기 부하 중 읽기 latency")
    @Test
    void read_under_hotspot_write_pressure() throws InterruptedException {
        // 백그라운드: 핫스팟 집중 쓰기
        for (int i = 0; i < USER_COUNT; i++) {
            final String userId = userIds.get(i);
            executor.submit(() -> {
                for (int j = 0; j < 10; j++) {
                    like(userId, "1");
                }
            });
        }

        // 전경: 읽기 latency 측정
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < READ_MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            readList();
            durations.add(elapsed(start));
        }

        printReadReport("like_desc 조회 (핫스팟 쓰기 부하 중)", durations);
    }

    // ─────────────────────────────────────────────
    // 공통
    // ─────────────────────────────────────────────

    private void createTestUsers() {
        // seed-data.sql로 생성된 seeduser_1 ~ seeduser_10000 사용
        for (int i = 1; i <= USER_COUNT; i++) {
            userIds.add(SEED_USER_PREFIX + i);
        }
    }

    private void signup(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "userId": "%s",
                  "password": "%s",
                  "name": "성능테스트유저",
                  "birthDate": "1995-01-01",
                  "email": "%s@test.com"
                }
                """.formatted(userId, PASSWORD, userId);
        restTemplate.exchange(
                BASE_URL + "/api/v1/users",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    private void like(String userId, String productId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", userId);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        try {
            restTemplate.exchange(
                    BASE_URL + "/api/v1/products/" + productId + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
        } catch (Exception ignored) {
            // 이미 좋아요한 경우(409) 무시
        }
    }

    private void readList() {
        restTemplate.exchange(LIST_ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private long elapsed(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    private List<Long> collect(List<Future<Long>> futures) {
        List<Long> result = new ArrayList<>();
        for (Future<Long> f : futures) {
            try {
                result.add(f.get());
            } catch (InterruptedException | ExecutionException ignored) {
                result.add(-1L);
            }
        }
        return result;
    }

    private void printWriteReport(String condition, List<Long> durations) {
        List<Long> valid = durations.stream().filter(d -> d >= 0).sorted().toList();
        if (valid.isEmpty()) {
            System.out.printf("%n[Performance] %s — 측정 데이터 없음%n", condition);
            return;
        }
        long min = valid.get(0);
        long max = valid.get(valid.size() - 1);
        long avg = (long) valid.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = valid.get((int) (valid.size() * 0.95));

        System.out.printf("""
                %n========================================
                [Performance] 좋아요 쓰기
                  조건  : %s
                  요청  : %d건 (동시)
                ----------------------------------------
                  min   : %dms
                  avg   : %dms
                  p95   : %dms
                  max   : %dms
                ========================================%n
                """, condition, valid.size(), min, avg, p95, max);
    }

    private void printReadReport(String condition, List<Long> durations) {
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long avg = (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p95 = sorted.get((int) (sorted.size() * 0.95));

        System.out.printf("""
                %n========================================
                [Performance] 목록 조회 (like_desc)
                  조건  : %s
                  측정  : %d회
                ----------------------------------------
                  min   : %dms
                  avg   : %dms
                  p50   : %dms
                  p95   : %dms
                  max   : %dms
                ========================================%n
                """, condition, sorted.size(), min, avg, p50, p95, max);
    }
}
