package com.loopers.interfaces.api.ranking;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TestRestTemplate testRestTemplate;
    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RankingV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandApplicationService brandApplicationService,
            ProductApplicationService productApplicationService,
            RedisTemplate<String, String> redisTemplate,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp,
            JdbcTemplate jdbcTemplate
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandApplicationService = brandApplicationService;
        this.productApplicationService = productApplicationService;
        this.redisTemplate = redisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void seedRanking(LocalDate date, String productId, double score) {
        redisTemplate.opsForZSet().add("ranking:all:" + date.format(DATE_FORMAT), productId, score);
    }

    private void seedWeeklyMv(LocalDate asOfDate, String productId, double score) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly (as_of_date, product_id, score, view_sum, like_delta_sum, purchase_quantity_sum, created_at)
                VALUES (?, ?, ?, 0, 0, 0, ?)
                """, asOfDate, productId, score, ZonedDateTime.now());
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("date의 랭킹 데이터가 있으면 200과 rank가 매겨진 상품 목록을 score 내림차순으로 반환한다.")
        @Test
        void returnsRankedProducts_whenRankingDataExists() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo first = productApplicationService.createProduct(brand.id(), "에어맥스", "설명", 100_000L, 10);
            ProductInfo second = productApplicationService.createProduct(brand.id(), "에어포스", "설명", 120_000L, 5);
            seedRanking(date, first.id(), 100.0);
            seedRanking(date, second.id(), 50.0);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?date=20260716&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingItemResponse> content = response.getBody().data().content();
            assertThat(content).hasSize(2);
            assertThat(content.get(0).id()).isEqualTo(first.id());
            assertThat(content.get(0).rank()).isEqualTo(1);
            assertThat(content.get(1).id()).isEqualTo(second.id());
            assertThat(content.get(1).rank()).isEqualTo(2);
        }

        @DisplayName("랭킹 상품은 score 내림차순으로 정렬되고 상품 정보가 함께 제공된다.")
        @Test
        void returnsRankedProductsWithAggregatedProductInformation_orderedByScoreDescending() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandInfo nike = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            BrandInfo adidas = brandApplicationService.createBrand("아디다스", "러닝 브랜드");
            ProductInfo lowerScoreProduct = productApplicationService.createProduct(nike.id(), "에어맥스", "운동화", 100_000L, 10);
            ProductInfo higherScoreProduct = productApplicationService.createProduct(adidas.id(), "울트라부스트", "러닝화", 120_000L, 5);
            seedRanking(date, lowerScoreProduct.id(), 0.6);
            seedRanking(date, higherScoreProduct.id(), 0.7);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?date=20260716&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingItemResponse> content = response.getBody().data().content();
            assertThat(content).hasSize(2);
            assertThat(content.get(0))
                    .extracting(
                            RankingV1Dto.RankingItemResponse::rank,
                            RankingV1Dto.RankingItemResponse::id,
                            RankingV1Dto.RankingItemResponse::brandId,
                            RankingV1Dto.RankingItemResponse::brandName,
                            RankingV1Dto.RankingItemResponse::name,
                            RankingV1Dto.RankingItemResponse::price,
                            RankingV1Dto.RankingItemResponse::likeCount
                    )
                    .containsExactly(
                            1L,
                            higherScoreProduct.id(),
                            adidas.id(),
                            "아디다스",
                            "울트라부스트",
                            120_000L,
                            0L
                    );
            assertThat(content.get(1))
                    .extracting(
                            RankingV1Dto.RankingItemResponse::rank,
                            RankingV1Dto.RankingItemResponse::id,
                            RankingV1Dto.RankingItemResponse::brandId,
                            RankingV1Dto.RankingItemResponse::brandName,
                            RankingV1Dto.RankingItemResponse::name,
                            RankingV1Dto.RankingItemResponse::price,
                            RankingV1Dto.RankingItemResponse::likeCount
                    )
                    .containsExactly(
                            2L,
                            lowerScoreProduct.id(),
                            nike.id(),
                            "나이키",
                            "에어맥스",
                            100_000L,
                            0L
                    );
        }

        @DisplayName("date 파라미터가 없으면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenDateIsMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("date 형식이 잘못되면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenDateFormatIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=2026-07-16", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 날짜이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenDateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260230", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("윤년이 아닌 해의 2월 29일이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenDateIsInvalidLeapDay() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20250229", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("page가 음수이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPageIsNegative() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260716&page=-1&size=20", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 1보다 작으면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeIsLessThanOne() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260716&page=0&size=0", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("해당 일자의 랭킹 데이터가 없으면 404를 반환한다.")
        @Test
        void returnsNotFound_whenRankingDataDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260101", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("period=WEEKLY이면 RDB MV 스냅샷을 조회한다.")
        @Test
        void returnsRankedProducts_whenPeriodIsWeekly() {
            // arrange
            LocalDate asOfDate = LocalDate.of(2026, 7, 23);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo first = productApplicationService.createProduct(brand.id(), "에어맥스", "설명", 100_000L, 10);
            ProductInfo second = productApplicationService.createProduct(brand.id(), "에어포스", "설명", 120_000L, 5);
            seedWeeklyMv(asOfDate, first.id(), 30.0);
            seedWeeklyMv(asOfDate, second.id(), 10.0);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?date=20260723&period=WEEKLY&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingItemResponse> content = response.getBody().data().content();
            assertThat(content).hasSize(2);
            assertThat(content.get(0).id()).isEqualTo(first.id());
            assertThat(content.get(0).rank()).isEqualTo(1);
            assertThat(content.get(1).id()).isEqualTo(second.id());
            assertThat(content.get(1).rank()).isEqualTo(2);
        }

        @DisplayName("해당 as_of_date의 WEEKLY MV 스냅샷이 없으면 404를 반환한다.")
        @Test
        void returnsNotFound_whenWeeklyMvSnapshotDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260101&period=WEEKLY", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("period 값이 열거형 밖이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "?date=20260716&period=YEARLY", HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
