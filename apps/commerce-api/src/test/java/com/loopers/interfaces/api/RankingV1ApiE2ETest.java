package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    @Qualifier("masterRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createProduct(Long brandId, String name) {
        ProductAdminInfo product = productApplicationService.createProduct(brandId, name, "설명", 10_000L, 10);
        return product.id();
    }

    private void seedRanking(LocalDate date, Long productId, double score) {
        String key = "ranking:all:" + date.format(KEY_DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    private ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> getRankings(LocalDate date) {
        return testRestTemplate.exchange(
            ENDPOINT + "?date=" + date.format(KEY_DATE_FORMAT) + "&page=1&size=20",
            HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {}
        );
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("점수 내림차순으로 정렬된, 상품/브랜드 정보가 조합된 랭킹을 반환한다.")
        @Test
        void returnsProductsOrderedByScoreDesc() {
            // arrange
            LocalDate today = LocalDate.now(ZONE);
            Long brandId = brandApplicationService.create("나이키", "스포츠 브랜드").id();
            Long lowScoreProduct = createProduct(brandId, "로우스코어");
            Long highScoreProduct = createProduct(brandId, "하이스코어");
            seedRanking(today, lowScoreProduct, 10.0);
            seedRanking(today, highScoreProduct, 100.0);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response = getRankings(today);

            // assert
            List<RankingV1Dto.RankingResponse> rankings = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(rankings).hasSize(2),
                () -> assertThat(rankings.get(0).rank()).isEqualTo(1L),
                () -> assertThat(rankings.get(0).productId()).isEqualTo(highScoreProduct),
                () -> assertThat(rankings.get(0).productName()).isEqualTo("하이스코어"),
                () -> assertThat(rankings.get(0).brandName()).isEqualTo("나이키"),
                () -> assertThat(rankings.get(0).fallback()).isFalse(),
                () -> assertThat(rankings.get(1).rank()).isEqualTo(2L),
                () -> assertThat(rankings.get(1).productId()).isEqualTo(lowScoreProduct)
            );
        }

        @DisplayName("다른 날짜로 조회하면, 조회 날짜의 랭킹만 반환하고 다른 날짜와 섞이지 않는다.")
        @Test
        void doesNotMixRankings_acrossDifferentDates() {
            // arrange
            LocalDate today = LocalDate.now(ZONE);
            LocalDate yesterday = today.minusDays(1);
            Long brandId = brandApplicationService.create("나이키", "스포츠 브랜드").id();
            Long todayProduct = createProduct(brandId, "오늘상품");
            Long yesterdayProduct = createProduct(brandId, "어제상품");
            seedRanking(today, todayProduct, 50.0);
            seedRanking(yesterday, yesterdayProduct, 999.0);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response = getRankings(today);

            // assert: 어제 점수가 훨씬 높아도 오늘 조회 결과엔 영향 없음
            List<RankingV1Dto.RankingResponse> rankings = response.getBody().data();
            assertThat(rankings).extracting(RankingV1Dto.RankingResponse::productId)
                .containsExactly(todayProduct);
        }

        @DisplayName("랭킹 데이터도 없고 상품도 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoRankingDataAndNoProducts() {
            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response = getRankings(LocalDate.now(ZONE));

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("랭킹 데이터는 없지만 상품이 있으면, 좋아요순 상품을 fallback=true로 반환한다.")
        @Test
        void fallsBackToPopularProducts_whenNoRankingDataButProductsExist() {
            // arrange: 서비스 오픈 초기처럼 랭킹 집계는 없지만 상품/좋아요는 존재하는 상황
            Long brandId = brandApplicationService.create("나이키", "스포츠 브랜드").id();
            Long lessLikedProduct = createProduct(brandId, "적은좋아요");
            Long moreLikedProduct = createProduct(brandId, "많은좋아요");
            likeApplicationService.like(1L, moreLikedProduct);
            likeApplicationService.like(2L, moreLikedProduct);
            likeApplicationService.like(1L, lessLikedProduct);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response = getRankings(LocalDate.now(ZONE));

            // assert
            List<RankingV1Dto.RankingResponse> rankings = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(rankings).hasSize(2),
                () -> assertThat(rankings).allMatch(RankingV1Dto.RankingResponse::fallback),
                () -> assertThat(rankings.get(0).productId()).isEqualTo(moreLikedProduct),
                () -> assertThat(rankings.get(0).rank()).isEqualTo(1L),
                () -> assertThat(rankings.get(1).productId()).isEqualTo(lessLikedProduct)
            );
        }
    }
}
