package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.support.ranking.RankingKeys;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig.class)
class RankingV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/rankings";
    private static final LocalDate DATE = LocalDate.of(2026, 7, 12);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductEntity saveProduct(Long brandId, String name, BigDecimal price) {
        return productJpaRepository.save(new ProductEntity(brandId, name, price));
    }

    private void addScore(LocalDate date, Long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKeys.daily(date), productId.toString(), score);
    }

    private void insertWeeklyMvRow(String periodKey, Long productId, int rank, double score) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_weekly (period_key, product_id, rank_position, score, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            periodKey, productId, rank, score
        );
    }

    private void insertMonthlyMvRow(String periodKey, Long productId, int rank, double score) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_monthly (period_key, product_id, rank_position, score, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            periodKey, productId, rank, score
        );
    }

    private ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> get(String url) {
        return testRestTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {}
        );
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("date를 주면, 해당 날짜의 랭킹을 점수 내림차순으로 상품정보와 함께 반환한다.")
        @Test
        void returnsRankingPage_withProductInfo_orderedByScoreDesc() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity first = saveProduct(brand.getId(), "상품A", BigDecimal.valueOf(12000));
            ProductEntity second = saveProduct(brand.getId(), "상품B", BigDecimal.valueOf(25000));
            ProductEntity third = saveProduct(brand.getId(), "상품C", BigDecimal.valueOf(9900));
            addScore(DATE, first.getId(), 87.3);
            addScore(DATE, second.getId(), 55.0);
            addScore(DATE, third.getId(), 30.5);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=" + DATE.format(DATE_FORMATTER));

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.date()).isEqualTo(DATE.format(DATE_FORMATTER)),
                () -> assertThat(data.totalCount()).isEqualTo(3L),
                () -> assertThat(data.items()).hasSize(3),
                () -> assertThat(data.items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(first.getId()),
                () -> assertThat(data.items().get(0).name()).isEqualTo("상품A"),
                () -> assertThat(data.items().get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(12000)),
                () -> assertThat(data.items().get(1).rank()).isEqualTo(2L),
                () -> assertThat(data.items().get(1).productId()).isEqualTo(second.getId()),
                () -> assertThat(data.items().get(2).rank()).isEqualTo(3L),
                () -> assertThat(data.items().get(2).productId()).isEqualTo(third.getId())
            );
        }

        @DisplayName("삭제된 상품은 랭킹 항목에서 제외된다.")
        @Test
        void excludesDeletedProduct_fromItems() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity alive = saveProduct(brand.getId(), "판매중", BigDecimal.valueOf(10000));
            ProductEntity deleted = saveProduct(brand.getId(), "삭제됨", BigDecimal.valueOf(20000));
            deleted.delete();
            productJpaRepository.save(deleted);
            addScore(DATE, deleted.getId(), 99.0);
            addScore(DATE, alive.getId(), 50.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=" + DATE.format(DATE_FORMATTER));

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(alive.getId()),
                () -> assertThat(data.items().get(0).rank()).isEqualTo(2L)
            );
        }

        @DisplayName("page, size로 두 번째 페이지를 조회하면, offset 이후 항목과 이어지는 순위를 반환한다.")
        @Test
        void returnsSecondPage_withContinuedRank() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity p1 = saveProduct(brand.getId(), "상품1", BigDecimal.valueOf(1000));
            ProductEntity p2 = saveProduct(brand.getId(), "상품2", BigDecimal.valueOf(2000));
            ProductEntity p3 = saveProduct(brand.getId(), "상품3", BigDecimal.valueOf(3000));
            addScore(DATE, p1.getId(), 30.0);
            addScore(DATE, p2.getId(), 20.0);
            addScore(DATE, p3.getId(), 10.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=" + DATE.format(DATE_FORMATTER) + "&page=2&size=2");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalCount()).isEqualTo(3L),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(p3.getId()),
                () -> assertThat(data.items().get(0).rank()).isEqualTo(3L)
            );
        }

        @DisplayName("date를 주지 않으면, 오늘 날짜의 랭킹을 반환한다.")
        @Test
        void returnsTodayRanking_whenDateIsOmitted() {
            LocalDate today = LocalDate.now(clock);
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "오늘의상품", BigDecimal.valueOf(15000));
            addScore(today, product.getId(), 42.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = get(BASE_URL);

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.date()).isEqualTo(today.format(DATE_FORMATTER)),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("오늘 랭킹이 새로 생성되어도 날짜를 지정하면 이전 날짜 랭킹을 반환한다.")
        @Test
        void returnsPreviousDateRanking_afterDateChanges() {
            LocalDate today = LocalDate.now(clock);
            LocalDate yesterday = today.minusDays(1);
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity yesterdayProduct = saveProduct(brand.getId(), "어제상품", BigDecimal.valueOf(10_000));
            ProductEntity todayProduct = saveProduct(brand.getId(), "오늘상품", BigDecimal.valueOf(20_000));
            addScore(yesterday, yesterdayProduct.getId(), 30.0);
            addScore(today, todayProduct.getId(), 50.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=" + yesterday.format(DATE_FORMATTER));

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.date()).isEqualTo(yesterday.format(DATE_FORMATTER)),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().getFirst().productId()).isEqualTo(yesterdayProduct.getId()),
                () -> assertThat(data.items().getFirst().name()).isEqualTo("어제상품")
            );
        }

        @DisplayName("랭킹 데이터가 없는 날짜면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenDateHasNoRanking() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=20200101");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalCount()).isEqualTo(0L),
                () -> assertThat(data.items()).isEmpty()
            );
        }

        @DisplayName("date 형식이 yyyyMMdd가 아니면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenDateFormatIsInvalid() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=2026-07-12");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("page가 1 미만이면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPageIsLessThanOne() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?page=0");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 100을 초과하면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeExceedsLimit() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?size=101");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY|MONTHLY")
    @Nested
    class GetRankingsByPeriod {

        @DisplayName("period=WEEKLY&periodKey를 주면, MV 랭킹을 순위 오름차순으로 상품정보와 함께 반환한다.")
        @Test
        void returnsWeeklyMvRanking_withProductInfo() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity first = saveProduct(brand.getId(), "상품A", BigDecimal.valueOf(12000));
            ProductEntity second = saveProduct(brand.getId(), "상품B", BigDecimal.valueOf(25000));
            insertWeeklyMvRow("2026W29", first.getId(), 1, 87.3);
            insertWeeklyMvRow("2026W29", second.getId(), 2, 55.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=WEEKLY&periodKey=2026W29");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.date()).isNull(),
                () -> assertThat(data.period()).isEqualTo("WEEKLY"),
                () -> assertThat(data.periodKey()).isEqualTo("2026W29"),
                () -> assertThat(data.totalCount()).isEqualTo(2L),
                () -> assertThat(data.items()).hasSize(2),
                () -> assertThat(data.items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(first.getId()),
                () -> assertThat(data.items().get(0).name()).isEqualTo("상품A"),
                () -> assertThat(data.items().get(1).rank()).isEqualTo(2L),
                () -> assertThat(data.items().get(1).productId()).isEqualTo(second.getId())
            );
        }

        @DisplayName("period=MONTHLY&periodKey를 주면, MV 랭킹을 반환한다.")
        @Test
        void returnsMonthlyMvRanking() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "월간상품", BigDecimal.valueOf(9900));
            insertMonthlyMvRow("202607", product.getId(), 1, 42.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=MONTHLY&periodKey=202607");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.period()).isEqualTo("MONTHLY"),
                () -> assertThat(data.periodKey()).isEqualTo("202607"),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("삭제된 상품은 MV 랭킹 항목에서도 제외된다.")
        @Test
        void excludesDeletedProduct_fromMvItems() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity alive = saveProduct(brand.getId(), "판매중", BigDecimal.valueOf(10000));
            ProductEntity deleted = saveProduct(brand.getId(), "삭제됨", BigDecimal.valueOf(20000));
            deleted.delete();
            productJpaRepository.save(deleted);
            insertWeeklyMvRow("2026W29", deleted.getId(), 1, 99.0);
            insertWeeklyMvRow("2026W29", alive.getId(), 2, 50.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=WEEKLY&periodKey=2026W29");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.items()).hasSize(1),
                () -> assertThat(data.items().get(0).productId()).isEqualTo(alive.getId()),
                () -> assertThat(data.items().get(0).rank()).isEqualTo(2L)
            );
        }

        @DisplayName("해당 periodKey에 랭킹 데이터가 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenPeriodKeyHasNoRanking() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=WEEKLY&periodKey=2026W01");

            RankingV1Dto.RankingPageResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalCount()).isEqualTo(0L),
                () -> assertThat(data.items()).isEmpty()
            );
        }

        @DisplayName("date와 period를 함께 주면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenDateAndPeriodBothGiven() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?date=20260719&period=WEEKLY&periodKey=2026W29");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("period만 있고 periodKey가 없으면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodWithoutPeriodKey() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=WEEKLY");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("periodKey 형식이 잘못되면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodKeyFormatIsInvalid() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=WEEKLY&periodKey=2026-29");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("지원하지 않는 period 값이면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodIsUnsupported() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get(BASE_URL + "?period=DAILY&periodKey=2026W29");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
