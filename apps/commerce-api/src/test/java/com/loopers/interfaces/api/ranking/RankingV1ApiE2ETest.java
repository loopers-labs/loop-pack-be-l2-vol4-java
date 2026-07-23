package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/rankings";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOURLY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository mvProductRankWeeklyJpaRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository mvProductRankMonthlyJpaRepository;

    @Autowired
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

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, price));
        productStatsRepository.save(new ProductStatsModel(product));
        stockRepository.save(new StockModel(product.getId(), 10L));
        return product;
    }

    private void seedScore(LocalDate date, Long productId, double score) {
        String key = "ranking:all:" + date.format(DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    private void seedHourlyScore(LocalDateTime dateTime, Long productId, double score) {
        String key = "ranking:hourly:" + dateTime.format(HOURLY_DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("date로 조회하면 점수 내림차순 상품 랭킹 페이지를 반환한다.")
        @Test
        void returnsRankingPage_whenDateIsProvided() {
            // given
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel high = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel low = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            seedScore(date, high.getId(), 100.0);
            seedScore(date, low.getId(), 50.0);

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?date=20260716&page=1&size=20", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(high.getId()),
                    () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("Nike")
            );
        }

        @DisplayName("데이터가 없는 날짜로 조회하면 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoDataForDate() {
            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?date=20200101", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("date 형식이 올바르지 않으면 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenDateFormatIsInvalid() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?date=2026-07-16", HttpMethod.GET, null, Void.class);

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY")
    @Nested
    class GetWeeklyRankings {

        @DisplayName("period=WEEKLY 로 조회하면 해당 주 MV 랭킹 페이지를 반환한다.")
        @Test
        void returnsWeeklyRankingPage_whenPeriodIsWeekly() {
            // given - 2024-01-03(수)이 속한 주의 월요일은 2024-01-01
            LocalDate weekStart = LocalDate.of(2024, 1, 1);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel first = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel second = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(weekStart, first.getId(), 100L, 1));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(weekStart, second.getId(), 60L, 2));

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            BASE_URL + "?date=20240103&period=WEEKLY&page=1&size=20", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(first.getId()),
                    () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("Nike")
            );
        }

        @DisplayName("지원하지 않는 period 로 조회하면 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?date=20240103&period=yearly", HttpMethod.GET, null, Void.class);

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @DisplayName("GET /api/v1/rankings?period=MONTHLY")
    @Nested
    class GetMonthlyRankings {

        @DisplayName("period=MONTHLY 로 조회하면 해당 월 MV 랭킹 페이지를 반환한다.")
        @Test
        void returnsMonthlyRankingPage_whenPeriodIsMonthly() {
            // given - 2024-01-15 가 속한 달의 1일은 2024-01-01
            LocalDate monthStart = LocalDate.of(2024, 1, 1);
            BrandModel brand = brandRepository.save(new BrandModel("Adidas"));
            ProductModel first = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel second = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            mvProductRankMonthlyJpaRepository.save(new MvProductRankMonthlyModel(monthStart, first.getId(), 500L, 1));
            mvProductRankMonthlyJpaRepository.save(new MvProductRankMonthlyModel(monthStart, second.getId(), 300L, 2));

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            BASE_URL + "?date=20240115&period=MONTHLY&page=1&size=20", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(first.getId()),
                    () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("Adidas")
            );
        }

        @DisplayName("주간 MV 만 있고 월간 MV 가 없으면 빈 페이지를 반환한다(주/월 소스가 섞이지 않는다).")
        @Test
        void returnsEmpty_whenOnlyWeeklyMvExists() {
            // given - 같은 기준일이라도 주간 MV 만 적재
            LocalDate weekStart = LocalDate.of(2024, 1, 1);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel product = saveProduct(brand.getId(), "주간전용", BigDecimal.valueOf(10000));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(weekStart, product.getId(), 100L, 1));

            // when - 월간으로 조회
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(
                            BASE_URL + "?date=20240115&period=MONTHLY", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings/hourly")
    @Nested
    class GetHourlyRankings {

        @DisplayName("dateTime으로 조회하면 점수 내림차순 상품 랭킹 페이지를 반환한다.")
        @Test
        void returnsRankingPage_whenDateTimeIsProvided() {
            // given
            LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 23, 0);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel high = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel low = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            seedHourlyScore(dateTime, high.getId(), 100.0);
            seedHourlyScore(dateTime, low.getId(), 50.0);

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "/hourly?dateTime=2026071623&page=1&size=20", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(high.getId())
            );
        }

        @DisplayName("데이터가 없는 시간으로 조회하면 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoDataForHour() {
            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "/hourly?dateTime=2020010100", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("dateTime 형식이 올바르지 않으면 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenDateTimeFormatIsInvalid() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/hourly?dateTime=2026-07-16-23", HttpMethod.GET, null, Void.class);

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }
}
