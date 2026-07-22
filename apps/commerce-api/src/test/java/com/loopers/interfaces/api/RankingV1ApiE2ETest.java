package com.loopers.interfaces.api;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final LocalDate DATE = LocalDate.of(2025, 9, 6);
    private static final String DATE_PARAM = "20250906";
    private static final RankingKey KEY = RankingKey.of(DATE);

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.masterRedisTemplate = masterRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductModel save(String name, long price) {
        return productJpaRepository.save(new ProductModel(name, name + " 설명", price, 1L));
    }

    private void seedScore(long productId, double score) {
        masterRedisTemplate.opsForZSet().add(KEY.value(), String.valueOf(productId), score);
    }

    private void seedWeeklyRank(String yearWeek, long productId, double score, int rankNo) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly (year_week, product_id, score, rank_no) VALUES (?, ?, ?, ?)",
            yearWeek, productId, score, rankNo
        );
    }

    @DisplayName("GET /api/v1/rankings 는 점수 순서대로 상품정보를 붙여 200 으로 반환한다.")
    @Test
    void returnsRankingPageWithProductAggregation() {
        // given
        ProductModel first = save("1위상품", 3_000L);
        ProductModel second = save("2위상품", 2_000L);
        seedScore(first.getId(), 3.0);
        seedScore(second.getId(), 2.0);

        // when
        ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + DATE_PARAM + "&page=0&size=10", HttpMethod.GET, null, responseType
        );

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().rankings()).hasSize(2),
            () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1L),
            () -> assertThat(response.getBody().data().rankings().get(0).product().name()).isEqualTo("1위상품"),
            () -> assertThat(response.getBody().data().rankings().get(0).product().price()).isEqualTo(3_000L),
            () -> assertThat(response.getBody().data().rankings().get(0).score()).isEqualTo(3.0),
            () -> assertThat(response.getBody().data().rankings().get(1).product().name()).isEqualTo("2위상품")
        );
    }

    @DisplayName("period=weekly 는 그 날짜가 속한 ISO 주(2025-W36)의 MV 랭킹을 rank 순서로 상품정보와 함께 반환한다.")
    @Test
    void returnsWeeklyRankingFromMv() {
        // given : 2025-09-06 은 2025-W36 (주간 MV 에 2건 적재)
        ProductModel first = save("주간1위", 5_000L);
        ProductModel second = save("주간2위", 4_000L);
        seedWeeklyRank("2025-W36", first.getId(), 7.0, 1);
        seedWeeklyRank("2025-W36", second.getId(), 3.0, 2);

        // when
        ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=weekly&date=" + DATE_PARAM + "&page=0&size=10", HttpMethod.GET, null, responseType
        );

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().rankings()).hasSize(2),
            () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1L),
            () -> assertThat(response.getBody().data().rankings().get(0).product().name()).isEqualTo("주간1위"),
            () -> assertThat(response.getBody().data().rankings().get(0).score()).isEqualTo(7.0),
            () -> assertThat(response.getBody().data().rankings().get(1).product().name()).isEqualTo("주간2위")
        );
    }

    @DisplayName("정의되지 않은 period 는 400 을 반환한다.")
    @Test
    void returnsBadRequestWhenPeriodUnknown() {
        // when
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=yearly&date=" + DATE_PARAM + "&page=0&size=10", HttpMethod.GET, null, responseType
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("date 파라미터가 없으면 400 을 반환한다.")
    @Test
    void returnsBadRequestWhenDateMissing() {
        // when
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            "/api/v1/rankings?page=0&size=10", HttpMethod.GET, null, responseType
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("date 형식이 yyyyMMdd 가 아니면 400 을 반환한다.")
    @Test
    void returnsBadRequestWhenDateMalformed() {
        // when
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=2025-09-06&page=0&size=10", HttpMethod.GET, null, responseType
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
