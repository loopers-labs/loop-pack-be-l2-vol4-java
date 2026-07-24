package com.loopers.interfaces.api;

import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/mv-product-rank-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RankingV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        RedisTemplate<String, String> redisTemplate,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        // MV 는 엔티티가 없어 DatabaseCleanUp 대상이 아니므로 직접 비운다
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private final ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType =
        new ParameterizedTypeReference<>() {};

    @DisplayName("GET /api/v1/rankings 는 date 랭킹을 점수순으로, 상품정보를 포함해 반환한다.")
    @Test
    void rankingPageReturnsAggregatedItems() {
        Product p1 = productJpaRepository.save(new Product(1L, "일등상품", "d", 5000L, 10));
        Product p2 = productJpaRepository.save(new Product(1L, "이등상품", "d", 3000L, 10));
        String key = RankingKeys.daily(LocalDate.of(2026, 7, 15));
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1.0);

        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=20260715&size=20&page=1", HttpMethod.GET, null, responseType);

        assertAll(
            () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(response.getBody().data().items())
                .extracting(RankingV1Dto.RankingItemResponse::productId)
                .containsExactly(p1.getId(), p2.getId()),
            () -> assertThat(response.getBody().data().items().get(0).name()).isEqualTo("일등상품"),
            () -> assertThat(response.getBody().data().items().get(0).rank()).isEqualTo(1L),
            () -> assertThat(response.getBody().data().totalCount()).isEqualTo(2L)
        );
    }

    @DisplayName("어제 날짜 랭킹도 date 파라미터로 조회된다 — 일자 변경 후 이전 랭킹 조회 검증.")
    @Test
    void yesterdayRankingQueryable() {
        Product p1 = productJpaRepository.save(new Product(1L, "어제일등", "d", 1000L, 10));
        LocalDate yesterday = LocalDate.now(RankingKeys.ZONE).minusDays(1);
        redisTemplate.opsForZSet().add(RankingKeys.daily(yesterday), String.valueOf(p1.getId()), 5.0);

        String date = yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + date, HttpMethod.GET, null, responseType);

        assertThat(response.getBody().data().items())
            .extracting(RankingV1Dto.RankingItemResponse::productId).containsExactly(p1.getId());
    }

    @DisplayName("period=WEEKLY 는 배치가 적재한 주간 MV 를 확정 순위대로 반환한다.")
    @Test
    void weeklyRankingReturnsMaterializedView() {
        Product p1 = productJpaRepository.save(new Product(1L, "주간일등", "d", 5000L, 10));
        Product p2 = productJpaRepository.save(new Product(1L, "주간이등", "d", 3000L, 10));
        insertWeekly("2026-W29", p1.getId(), 1, 30.0);
        insertWeekly("2026-W29", p2.getId(), 2, 12.0);

        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY&date=20260715&size=20&page=1", HttpMethod.GET, null, responseType);

        assertAll(
            () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(response.getBody().data().period()).isEqualTo("WEEKLY"),
            () -> assertThat(response.getBody().data().periodKey()).isEqualTo("2026-W29"),
            () -> assertThat(response.getBody().data().items())
                .extracting(RankingV1Dto.RankingItemResponse::productId)
                .containsExactly(p1.getId(), p2.getId()),
            () -> assertThat(response.getBody().data().items().get(0).name()).isEqualTo("주간일등"),
            () -> assertThat(response.getBody().data().totalCount()).isEqualTo(2L)
        );
    }

    @DisplayName("period=MONTHLY 는 월간 MV 를 반환한다.")
    @Test
    void monthlyRankingReturnsMonthlyView() {
        Product p1 = productJpaRepository.save(new Product(1L, "월간일등", "d", 1000L, 10));
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_monthly
              (period_key, product_id, rank_no, score, like_count, order_count, view_count, created_at, updated_at)
            VALUES ('2026-07', ?, 1, 88.0, 0, 0, 0, NOW(), NOW())
            """, p1.getId());

        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=MONTHLY&date=20260715", HttpMethod.GET, null, responseType);

        assertAll(
            () -> assertThat(response.getBody().data().periodKey()).isEqualTo("2026-07"),
            () -> assertThat(response.getBody().data().items())
                .extracting(RankingV1Dto.RankingItemResponse::productId).containsExactly(p1.getId())
        );
    }

    @DisplayName("잘못된 date 형식은 400 이다.")
    @Test
    void invalidDateRejected() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=2026-07-15", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("알 수 없는 period 는 400 이다.")
    @Test
    void invalidPeriodRejected() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=YEARLY", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void insertWeekly(String periodKey, Long productId, int rankNo, double score) {
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_weekly
              (period_key, product_id, rank_no, score, like_count, order_count, view_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, 0, 0, NOW(), NOW())
            """, periodKey, productId, rankNo, score);
    }
}
