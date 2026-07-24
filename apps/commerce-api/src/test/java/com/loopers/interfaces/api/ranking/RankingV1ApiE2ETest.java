package com.loopers.interfaces.api.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.ranking.DailyRankingKey;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RankingV1ApiE2ETest.FixedClockTestConfiguration.class)
class RankingV1ApiE2ETest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-19T03:00:00Z"),
        ZoneId.of("Asia/Seoul")
    );
    private final TestRestTemplate restTemplate;
    private final BrandJpaRepository brandRepository;
    private final ProductJpaRepository productRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RankingV1ApiE2ETest(
        TestRestTemplate restTemplate,
        BrandJpaRepository brandRepository,
        ProductJpaRepository productRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        MeterRegistry meterRegistry,
        JdbcTemplate jdbcTemplate
    ) {
        this.restTemplate = restTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("과거 날짜의 랭킹을 Redis 순서와 원래 순위로 상품·브랜드 정보와 함께 반환한다.")
    @Test
    void returnsAggregatedRankingInRedisOrder() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 16);
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity first = saveProduct(brand.getId(), "1위 상품");
        ProductJpaEntity third = saveProduct(brand.getId(), "3위 상품");
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, first.getId().toString(), 10.0);
        redisTemplate.opsForZSet().add(key, "999999", 7.0);
        redisTemplate.opsForZSet().add(key, third.getId().toString(), 3.0);

        // act
        ResponseEntity<ApiResponse<List<RankingDto.Response>>> response = restTemplate.exchange(
            "/api/v1/rankings?date=20260716&page=1&size=20",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            responseType()
        );

        // assert
        List<RankingDto.Response> data = response.getBody().data();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(data).extracting(RankingDto.Response::rank).containsExactly(1L, 3L),
            () -> assertThat(data).extracting(item -> item.product().id()).containsExactly(first.getId(), third.getId()),
            () -> assertThat(data.get(0).product().brand().name()).isEqualTo("Loopers"),
            () -> assertThat(meterRegistry.counter("ranking_query_total", "result", "hit").count()).isPositive(),
            () -> assertThat(meterRegistry.timer("ranking_api_duration").count()).isPositive()
        );
    }

    @DisplayName("랭킹 페이지 번호는 1부터 시작한다.")
    @Test
    void usesOneBasedPage() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity first = saveProduct(brand.getId(), "1위");
        ProductJpaEntity second = saveProduct(brand.getId(), "2위");
        ProductJpaEntity zero = saveProduct(brand.getId(), "0점");
        ProductJpaEntity negative = saveProduct(brand.getId(), "음수점");
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, first.getId().toString(), 2.0);
        redisTemplate.opsForZSet().add(key, second.getId().toString(), 1.0);
        redisTemplate.opsForZSet().add(key, zero.getId().toString(), 0.0);
        redisTemplate.opsForZSet().add(key, negative.getId().toString(), -1.0);

        ResponseEntity<ApiResponse<List<RankingDto.Response>>> response = restTemplate.exchange(
            "/api/v1/rankings?date=20260716&page=2&size=1",
            HttpMethod.GET, HttpEntity.EMPTY, responseType()
        );

        assertThat(response.getBody().data()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(2L);
            assertThat(item.product().id()).isEqualTo(second.getId());
        });
    }

    @DisplayName("랭킹 목록은 원점수가 양수인 상품만 반환한다.")
    @Test
    void excludesNonPositiveScoresFromRankingList() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity positive = saveProduct(brand.getId(), "양수점");
        ProductJpaEntity zero = saveProduct(brand.getId(), "0점");
        ProductJpaEntity negative = saveProduct(brand.getId(), "음수점");
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, positive.getId().toString(), 1.0);
        redisTemplate.opsForZSet().add(key, zero.getId().toString(), 0.0);
        redisTemplate.opsForZSet().add(key, negative.getId().toString(), -1.0);

        ResponseEntity<ApiResponse<List<RankingDto.Response>>> response = restTemplate.exchange(
            "/api/v1/rankings?date=20260716&page=1&size=20",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            responseType()
        );

        assertThat(response.getBody().data()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(1L);
            assertThat(item.product().id()).isEqualTo(positive.getId());
        });
    }

    @DisplayName("page가 1보다 작으면 400을 반환한다.")
    @Test
    void rejectsInvalidPage() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/rankings?page=0", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("랭킹 결과가 없으면 miss 관측 지표를 기록한다.")
    @Test
    void recordsRankingMissMetric() {
        restTemplate.getForEntity("/api/v1/rankings?date=20260716", String.class);

        assertThat(meterRegistry.counter("ranking_query_total", "result", "miss").count()).isPositive();
    }

    @DisplayName("WEEKLY는 requestDate가 속한 주의 월요일 MV를 페이지 단위로 조회한다.")
    @Test
    void returnsWeeklyRankingFromDatabase() {
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity first = saveProduct(brand.getId(), "주간 1위");
        ProductJpaEntity second = saveProduct(brand.getId(), "주간 2위");
        insertPeriodRank("mv_product_rank_weekly", LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 12), first.getId(), 1, 10.0);
        insertPeriodRank("mv_product_rank_weekly", LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 12), second.getId(), 2, 5.0);

        ResponseEntity<ApiResponse<List<RankingDto.Response>>> response = restTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY&date=20260709&page=2&size=1",
            HttpMethod.GET, HttpEntity.EMPTY, responseType()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(2);
            assertThat(item.product().id()).isEqualTo(second.getId());
        });
    }

    @DisplayName("MONTHLY는 requestDate가 속한 달의 1일 MV를 조회한다.")
    @Test
    void returnsMonthlyRankingFromDatabase() {
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity product = saveProduct(brand.getId(), "월간 1위");
        insertPeriodRank("mv_product_rank_monthly", LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30), product.getId(), 1, 12.0);

        ResponseEntity<ApiResponse<List<RankingDto.Response>>> response = restTemplate.exchange(
            "/api/v1/rankings?period=MONTHLY&date=20260620",
            HttpMethod.GET, HttpEntity.EMPTY, responseType()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).singleElement().satisfies(item ->
            assertThat(item.product().id()).isEqualTo(product.getId())
        );
    }

    @DisplayName("지원하지 않는 period는 400을 반환한다.")
    @Test
    void rejectsInvalidPeriod() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/rankings?period=YEARLY",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("아직 종료되지 않은 주간·월간 랭킹 조회는 400을 반환한다.")
    @Test
    void rejectsOpenPeriod() {
        String today = LocalDate.now(FIXED_CLOCK)
            .format(DateTimeFormatter.BASIC_ISO_DATE);

        ResponseEntity<String> weekly = restTemplate.getForEntity(
            "/api/v1/rankings?period=WEEKLY&date=" + today,
            String.class
        );
        ResponseEntity<String> monthly = restTemplate.getForEntity(
            "/api/v1/rankings?period=MONTHLY&date=" + today,
            String.class
        );

        assertAll(
            () -> assertThat(weekly.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
            () -> assertThat(monthly.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @DisplayName("상품 상세 순위는 상품 캐시와 분리되어 매 요청마다 최신값을 반환한다.")
    @Test
    void returnsLatestRankIndependentlyFromProductCache() {
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity target = saveProduct(brand.getId(), "대상 상품");
        ProductJpaEntity other = saveProduct(brand.getId(), "다른 상품");
        String key = DailyRankingKey.from(LocalDate.now(FIXED_CLOCK));
        redisTemplate.opsForZSet().add(key, other.getId().toString(), 2.0);
        redisTemplate.opsForZSet().add(key, target.getId().toString(), 1.0);

        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> first = restTemplate.exchange(
            "/api/v1/products/" + target.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );
        redisTemplate.opsForZSet().incrementScore(key, target.getId().toString(), 2.0);
        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> second = restTemplate.exchange(
            "/api/v1/products/" + target.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );

        assertAll(
            () -> assertThat(first.getBody().data().rank()).isEqualTo(2L),
            () -> assertThat(second.getBody().data().rank()).isEqualTo(1L),
            () -> assertThat(redisTemplate.opsForValue().get("product:detail:" + target.getId())).doesNotContain("rank")
        );
    }

    @DisplayName("랭킹에 없는 상품은 상세 순위를 null로 반환한다.")
    @Test
    void returnsNullRankWhenProductIsNotRanked() {
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity product = saveProduct(brand.getId(), "미진입 상품");

        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> response = restTemplate.exchange(
            "/api/v1/products/" + product.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );

        assertThat(response.getBody().data().rank()).isNull();
    }

    @DisplayName("원점수가 0 이하인 상품은 상세 순위를 null로 반환한다.")
    @Test
    void returnsNullRankWhenRawScoreIsNotPositive() {
        BrandJpaEntity brand = saveBrand();
        ProductJpaEntity positive = saveProduct(brand.getId(), "양수점 상품");
        ProductJpaEntity zero = saveProduct(brand.getId(), "0점 상품");
        ProductJpaEntity negative = saveProduct(brand.getId(), "음수점 상품");
        String key = DailyRankingKey.from(LocalDate.now(FIXED_CLOCK));
        redisTemplate.opsForZSet().add(key, positive.getId().toString(), 1.0);
        redisTemplate.opsForZSet().add(key, zero.getId().toString(), 0.0);
        redisTemplate.opsForZSet().add(key, negative.getId().toString(), -1.0);

        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> positiveResponse = restTemplate.exchange(
            "/api/v1/products/" + positive.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );
        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> zeroResponse = restTemplate.exchange(
            "/api/v1/products/" + zero.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );
        ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> negativeResponse = restTemplate.exchange(
            "/api/v1/products/" + negative.getId(), HttpMethod.GET, HttpEntity.EMPTY, productResponseType()
        );

        assertAll(
            () -> assertThat(positiveResponse.getBody().data().rank()).isEqualTo(1L),
            () -> assertThat(zeroResponse.getBody().data().rank()).isNull(),
            () -> assertThat(negativeResponse.getBody().data().rank()).isNull()
        );
    }

    private BrandJpaEntity saveBrand() {
        return brandRepository.save(BrandJpaEntity.from(new Brand("Loopers", "브랜드")));
    }

    private void insertPeriodRank(
        String table,
        LocalDate periodStart,
        LocalDate periodEnd,
        long productId,
        int rank,
        double score
    ) {
        jdbcTemplate.update("""
            INSERT INTO %s
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 0, 0, 0, NOW(6), NOW(6))
            """.formatted(table), periodStart, periodEnd, productId, rank, score);
    }

    private ProductJpaEntity saveProduct(Long brandId, String name) {
        return productRepository.save(ProductJpaEntity.from(new Product(brandId, name, "설명", 10_000L, 10)));
    }

    private ParameterizedTypeReference<ApiResponse<List<RankingDto.Response>>> responseType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    private ParameterizedTypeReference<ApiResponse<ProductDto.Get.V1.Response>> productResponseType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    @TestConfiguration
    static class FixedClockTestConfiguration {
        @Bean
        @Primary
        Clock rankingTestClock() {
            return FIXED_CLOCK;
        }
    }
}
