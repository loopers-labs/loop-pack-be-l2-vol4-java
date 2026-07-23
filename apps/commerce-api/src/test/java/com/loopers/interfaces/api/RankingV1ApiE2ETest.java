package com.loopers.interfaces.api;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "queue.scheduler.enabled=false"
)
class RankingV1ApiE2ETest {

    private static final String RANKING_ENDPOINT = "/api/v1/rankings";
    private static final String PRODUCT_ENDPOINT = "/api/v1/products";
    private static final String RAW_PASSWORD = "Password1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private BrandModel savedBrand;
    private UserModel testUser;

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        testUser = userJpaRepository.save(new UserModel(
            "testuser", passwordEncoder.encode(RAW_PASSWORD),
            "테스터", LocalDate.of(1990, 1, 15), "test@example.com"
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductModel saveProduct(String name, int price, int stock) {
        ProductModel product = productJpaRepository.save(new ProductModel(savedBrand, name, price));
        stockJpaRepository.save(new StockModel(product, stock));
        return product;
    }

    private void addScore(LocalDate date, Long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKey.daily(date), String.valueOf(productId), score);
    }

    /**
     * 주간 MV(commerce-batch 소유 테이블)를 테스트에서 직접 만들어 비운다.
     * commerce-api는 이 테이블을 JdbcTemplate으로 읽기만 하므로(엔티티 없음) 테스트가 스키마를 생성한다.
     */
    private void resetWeeklyMv() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS mv_product_rank_weekly ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rank_no INT NOT NULL, product_id BIGINT NOT NULL, "
                + "score DOUBLE, period_start DATE, period_end DATE, created_at DATETIME, updated_at DATETIME)"
        );
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
    }

    /** 특정 기간 스냅샷을 순위 순으로 시드한다 (기존 행은 지우지 않음 — 여러 기간 히스토리 시드 가능). */
    private void seedWeeklyMv(LocalDate periodStart, LocalDate periodEnd, List<Long> productIdsInRankOrder) {
        for (int i = 0; i < productIdsInRankOrder.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly (rank_no, product_id, score, period_start, period_end) VALUES (?, ?, ?, ?, ?)",
                i + 1, productIdsInRankOrder.get(i), 0.0, periodStart, periodEnd
            );
        }
    }

    private HttpHeaders userAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", testUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    @DisplayName("GET /api/v1/rankings 요청 시,")
    @Nested
    class GetRankings {

        @DisplayName("점수 내림차순 순위와 함께 상품정보(이름·가격·브랜드)가 Aggregation 되어 반환된다.")
        @Test
        void returnsRankedProducts_withAggregatedProductInfo() {
            // arrange
            ProductModel first = saveProduct("에어맥스", 150_000, 50);
            ProductModel second = saveProduct("조던", 200_000, 30);
            addScore(LocalDate.now(), first.getId(), 6_000.0);
            addScore(LocalDate.now(), second.getId(), 0.3);

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT, HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.totalCount()).isEqualTo(2),
                () -> assertThat(body.items()).hasSize(2),
                () -> assertThat(body.items().get(0).rank()).isEqualTo(1),
                () -> assertThat(body.items().get(0).productId()).isEqualTo(first.getId()),
                () -> assertThat(body.items().get(0).name()).isEqualTo("에어맥스"),
                () -> assertThat(body.items().get(0).price()).isEqualTo(150_000),
                () -> assertThat(body.items().get(0).brandName()).isEqualTo("Nike"),
                () -> assertThat(body.items().get(1).rank()).isEqualTo(2),
                () -> assertThat(body.items().get(1).productId()).isEqualTo(second.getId())
            );
        }

        @DisplayName("date 파라미터로 이전 날짜의 랭킹을 조회할 수 있다.")
        @Test
        void returnsPreviousDayRanking_whenDateIsGiven() {
            // arrange
            ProductModel product = saveProduct("에어맥스", 150_000, 50);
            LocalDate yesterday = LocalDate.now().minusDays(1);
            addScore(yesterday, product.getId(), 0.6);

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?date=" + yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert — 어제 랭킹판에서 조회되고, 오늘 랭킹판에는 없다
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("랭킹 데이터가 없는 날짜는 빈 목록이 반환된다.")
        @Test
        void returnsEmptyItems_whenNoRankingForDate() {
            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?date=20200101",
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().items()).isEmpty(),
                () -> assertThat(response.getBody().data().totalCount()).isZero()
            );
        }

        @DisplayName("잘못된 date 형식이면 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenDateFormatIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?date=2026-07-15",
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY/MONTHLY 요청 시,")
    @Nested
    class GetRankingsByPeriod {

        @DisplayName("period=WEEKLY(date 미지정)는 MV의 최신 스냅샷을 순위대로 반환하고, 응답 date에 period_end를 담는다.")
        @Test
        void returnsWeeklyRankingFromMv() {
            // arrange — 주간 MV 최신 스냅샷([07-08..07-14])에 순위 1·2로 시드 (일간 ZSET은 비어 있음)
            ProductModel first = saveProduct("에어맥스", 150_000, 50);
            ProductModel second = saveProduct("조던", 200_000, 30);
            LocalDate periodStart = LocalDate.of(2026, 7, 8);
            LocalDate periodEnd = LocalDate.of(2026, 7, 14);
            resetWeeklyMv();
            seedWeeklyMv(periodStart, periodEnd, List.of(first.getId(), second.getId()));

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?period=WEEKLY", HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.period()).isEqualTo("WEEKLY"),
                () -> assertThat(body.date()).isEqualTo(periodEnd.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)),
                () -> assertThat(body.totalCount()).isEqualTo(2),
                () -> assertThat(body.items().get(0).rank()).isEqualTo(1),
                () -> assertThat(body.items().get(0).productId()).isEqualTo(first.getId()),
                () -> assertThat(body.items().get(0).name()).isEqualTo("에어맥스"),
                () -> assertThat(body.items().get(0).brandName()).isEqualTo("Nike"),
                () -> assertThat(body.items().get(1).rank()).isEqualTo(2),
                () -> assertThat(body.items().get(1).productId()).isEqualTo(second.getId())
            );
        }

        @DisplayName("period=WEEKLY&date=<과거 기간 내 날짜>는 그 날짜를 포함하는 과거 스냅샷을 반환한다 (히스토리).")
        @Test
        void returnsWeeklyHistoryByDate() {
            // arrange — 두 개의 겹치지 않는 주간 스냅샷을 히스토리로 시드
            ProductModel older = saveProduct("과거상품", 100_000, 50);
            ProductModel latest = saveProduct("최신상품", 200_000, 30);
            resetWeeklyMv();
            seedWeeklyMv(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), List.of(older.getId()));    // 과거 창
            seedWeeklyMv(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 14), List.of(latest.getId()));  // 최신 창

            // act — 과거 창 안의 날짜(07-03)로 조회
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?period=WEEKLY&date=20260703", HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert — 최신이 아니라 07-03을 포함하는 과거 스냅샷(07-01..07-07)이 반환된다
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.date()).isEqualTo("20260707"),
                () -> assertThat(body.totalCount()).isEqualTo(1),
                () -> assertThat(body.items()).hasSize(1),
                () -> assertThat(body.items().get(0).productId()).isEqualTo(older.getId())
            );
        }

        @DisplayName("period 미지정 시 기존과 동일하게 일간(DAILY) 랭킹으로 동작한다 (하위호환).")
        @Test
        void defaultsToDaily_whenPeriodOmitted() {
            // arrange — 일간 ZSET에만 점수 적재
            ProductModel product = saveProduct("에어맥스", 150_000, 50);
            addScore(LocalDate.now(), product.getId(), 6_000.0);

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT, HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.period()).isEqualTo("DAILY"),
                () -> assertThat(body.date()).isNotNull(),
                () -> assertThat(body.items()).hasSize(1),
                () -> assertThat(body.items().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("지원하지 않는 period 값이면 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                RANKING_ENDPOINT + "?period=YEARLY", HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{productId} 상세 조회 시,")
    @Nested
    class GetProductWithRank {

        @DisplayName("오늘 랭킹판에 있는 상품은 순위(rank)가 함께 반환된다.")
        @Test
        void returnsRank_whenProductIsRankedToday() {
            // arrange — 2위 상품 조회
            ProductModel first = saveProduct("에어맥스", 150_000, 50);
            ProductModel second = saveProduct("조던", 200_000, 30);
            addScore(LocalDate.now(), first.getId(), 6_000.0);
            addScore(LocalDate.now(), second.getId(), 0.3);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + second.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isEqualTo(2L)
            );
        }

        @DisplayName("오늘 랭킹판에 없는 상품은 rank가 null로 반환된다.")
        @Test
        void returnsNullRank_whenProductIsNotRanked() {
            // arrange
            ProductModel product = saveProduct("에어맥스", 150_000, 50);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + product.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isNull()
            );
        }
    }
}
