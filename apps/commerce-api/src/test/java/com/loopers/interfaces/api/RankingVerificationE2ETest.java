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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingVerificationE2ETest {

    // streamer RankingScorePolicy 의 가중치를 옮겨 온 "시나리오 재현용" 상수.
    // 이 테스트는 정책 값을 검증하지 않는다(그건 RankingScorePolicyTest 의 몫).
    // 그 값이 만든 우열이 읽기 API 의 rank 순서로 관측되는지만 본다.
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RankingVerificationE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.masterRedisTemplate = masterRedisTemplate;
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

    private void seedScore(RankingKey key, long productId, double score) {
        masterRedisTemplate.opsForZSet().add(key.value(), String.valueOf(productId), score);
    }

    private ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getRankings(String dateParam) {
        ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/rankings?date=" + dateParam + "&page=0&size=10", HttpMethod.GET, null, responseType
        );
    }

    @DisplayName("주문 1건 상품이 좋아요 3건 상품보다 위(rank 작음)로 랭킹 응답에 나타난다.")
    @Test
    void oneOrderOutranksThreeLikesInRankingResponse() {
        // given
        LocalDate date = LocalDate.of(2025, 9, 6);
        RankingKey key = RankingKey.of(date);
        ProductModel orderProduct = save("주문상품", 5_000L);
        ProductModel likeProduct = save("좋아요상품", 5_000L);
        long lineAmount = 100_000L;
        double oneBigOrderScore = ORDER_WEIGHT * Math.log10(1 + lineAmount);   // 주문 1건 (큰 라인금액)
        double threeLikesScore = 3 * LIKE_WEIGHT;                              // 좋아요 3건
        seedScore(key, orderProduct.getId(), oneBigOrderScore);
        seedScore(key, likeProduct.getId(), threeLikesScore);

        // when
        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = getRankings(date.format(YYYYMMDD));

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().rankings()).hasSize(2),
            () -> assertThat(response.getBody().data().rankings().get(0).product().name()).isEqualTo("주문상품"),
            () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1L),
            () -> assertThat(response.getBody().data().rankings().get(1).product().name()).isEqualTo("좋아요상품"),
            () -> assertThat(response.getBody().data().rankings().get(1).rank()).isEqualTo(2L),
            () -> assertThat(response.getBody().data().rankings().get(0).score())
                .isGreaterThan(response.getBody().data().rankings().get(1).score())
        );
    }

    @DisplayName("일자가 바뀌어도 어제 날짜의 랭킹을 date 파라미터로 조회할 수 있다.")
    @Test
    void readsYesterdayRankingAfterDayRollover() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RankingKey key = RankingKey.of(yesterday);
        ProductModel product = save("어제인기상품", 4_000L);
        seedScore(key, product.getId(), 3.0);

        // when
        ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = getRankings(yesterday.format(YYYYMMDD));

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().rankings()).hasSize(1),
            () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1L),
            () -> assertThat(response.getBody().data().rankings().get(0).product().name()).isEqualTo("어제인기상품")
        );
    }
}
