package com.loopers.interfaces.api.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성능 테스트 — 실행 중인 로컬 서버 대상 (Spring 컨텍스트 미기동)
 *
 * 사전 조건:
 *   1. docker-compose -f ./docker/infra-compose.yml up -d
 *   2. docker exec -i docker-mysql-1 mysql -u application -papplication loopers < docker/seed-data.sql
 *   3. ./gradlew :apps:commerce-api:bootRun
 *
 * 실행:
 *   ./gradlew :apps:commerce-api:test -Dgroups="performance"
 */
@Tag("performance")
class ProductListingPerformanceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String ENDPOINT_WITH_BRAND = BASE_URL + "/api/v1/products?brandId=15&sort=price_desc&page=0&size=20";
    private static final String ENDPOINT_ALL = BASE_URL + "/api/v1/products?sort=price_desc&page=0&size=20";

    private static final int WARMUP_COUNT = 3;
    private static final int MEASURE_COUNT = 20;

    private final RestTemplate restTemplate = new RestTemplate();

    @DisplayName("[성능] brandId 필터 조건 상품 목록 가격 내림차순 조회 응답 시간 측정")
    @Test
    void measureProductListingResponseTime_byBrandId_sortByPriceDesc() {
        for (int i = 0; i < WARMUP_COUNT; i++) {
            call(ENDPOINT_WITH_BRAND);
        }

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            ResponseEntity<String> response = call(ENDPOINT_WITH_BRAND);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            durations.add(elapsedMs);
        }

        printReport("brandId=15, sort=price_desc, page=0, size=20", durations);
    }

    @DisplayName("[성능] brandId 없이 전체 상품 목록 가격 내림차순 조회 응답 시간 측정")
    @Test
    void measureProductListingResponseTime_allBrands_sortByPriceDesc() {
        for (int i = 0; i < WARMUP_COUNT; i++) {
            call(ENDPOINT_ALL);
        }

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            ResponseEntity<String> response = call(ENDPOINT_ALL);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            durations.add(elapsedMs);
        }

        printReport("brandId=없음(전체), sort=price_desc, page=0, size=20", durations);
    }

    private ResponseEntity<String> call(String endpoint) {
        return restTemplate.exchange(endpoint, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private void printReport(String condition, List<Long> durations) {
        Collections.sort(durations);

        long min = durations.get(0);
        long max = durations.get(durations.size() - 1);
        long avg = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = durations.get((int) (durations.size() * 0.50));
        long p95 = durations.get((int) (durations.size() * 0.95));

        System.out.printf("""
                %n========================================
                [Performance] 상품 목록 조회
                  조건  : %s
                  측정  : %d회 (워밍업 %d회 제외)
                ----------------------------------------
                  min   : %dms
                  avg   : %dms
                  p50   : %dms
                  p95   : %dms
                  max   : %dms
                ========================================%n
                """, condition, MEASURE_COUNT, WARMUP_COUNT, min, avg, p50, p95, max);
    }
}
