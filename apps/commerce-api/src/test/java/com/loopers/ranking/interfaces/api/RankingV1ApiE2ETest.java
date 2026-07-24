package com.loopers.ranking.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.product.application.ProductLikeSummaryWriter;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig.class)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT_RANKINGS = "/api/v1/rankings";
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 14);

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductLikeSummaryWriter productLikeSummaryWriter;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductLikeSummaryWriter productLikeSummaryWriter,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productLikeSummaryWriter = productLikeSummaryWriter;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.masterRedisTemplate = masterRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("일간 Ranking이 있으면 200 OK와 순위 및 상품 정보 Page를 반환한다")
        @Test
        void returnsRankingPage_whenDailyRankingExists() throws Exception {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product standard = createProduct(brand, "아이폰 16", 1_250_000L);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L);
            addRanking(standard, 1.0);
            addRanking(pro, 2.0);

            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE),
                String.class
            );

            // assert
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.path("content").get(0).path("rank").asLong()).isEqualTo(1),
                () -> assertThat(data.path("content").get(0).path("product").path("name").asText())
                    .isEqualTo("아이폰 16 Pro"),
                () -> assertThat(data.path("content").get(0).path("product").path("brand").path("name").asText())
                    .isEqualTo("애플"),
                () -> assertThat(data.path("content").get(0).path("product").path("price").asLong())
                    .isEqualTo(1_550_000L),
                () -> assertThat(data.path("content").get(0).path("product").path("likeCount").asLong())
                    .isZero(),
                () -> assertThat(data.path("content").get(1).path("rank").asLong()).isEqualTo(2),
                () -> assertThat(data.path("content").get(1).path("product").path("name").asText())
                    .isEqualTo("아이폰 16"),
                () -> assertThat(data.path("totalElements").asLong()).isEqualTo(2),
                () -> assertThat(data.path("totalPages").asInt()).isEqualTo(1),
                () -> assertThat(data.path("number").asInt()).isZero(),
                () -> assertThat(data.path("size").asInt()).isEqualTo(20),
                () -> assertThat(data.path("first").asBoolean()).isTrue(),
                () -> assertThat(data.path("last").asBoolean()).isTrue()
            );
        }

        @DisplayName("주간·월간 완료 Snapshot이 있으면 저장된 순위와 상품 정보 Page를 반환한다")
        @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
        @ParameterizedTest
        void returnsStoredRankingPage_whenPublishedSnapshotExists(
            RankingPeriod period
        ) throws Exception {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product standard = createProduct(brand, "아이폰 16", 1_250_000L);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L);
            insertCompletedSnapshot(period, 1L);
            insertPublishedRank(period, 1L, pro.getId(), 2);
            insertPublishedRank(period, 1L, standard.getId(), 4);

            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&period=" + period.name(),
                String.class
            );

            // assert
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.path("content").get(0).path("rank").asLong()).isEqualTo(2),
                () -> assertThat(data.path("content").get(0).path("product").path("name").asText())
                    .isEqualTo("아이폰 16 Pro"),
                () -> assertThat(data.path("content").get(1).path("rank").asLong()).isEqualTo(4),
                () -> assertThat(data.path("content").get(1).path("product").path("name").asText())
                    .isEqualTo("아이폰 16"),
                () -> assertThat(data.path("totalElements").asLong()).isEqualTo(2)
            );
        }

        @DisplayName("페이지 크기가 100을 초과하면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenPageSizeExceedsMaximum() {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&size=101",
                String.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("지원하지 않는 Ranking 기간이면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&period=YEARLY",
                String.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("Ranking Key가 없으면 200 OK와 빈 Page를 반환한다")
        @Test
        void returnsEmptyPage_whenRankingKeyDoesNotExist() throws Exception {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE),
                String.class
            );

            // assert
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.path("content")).isEmpty(),
                () -> assertThat(data.path("totalElements").asLong()).isZero(),
                () -> assertThat(data.path("totalPages").asInt()).isZero(),
                () -> assertThat(data.path("number").asInt()).isZero(),
                () -> assertThat(data.path("size").asInt()).isEqualTo(20),
                () -> assertThat(data.path("first").asBoolean()).isTrue(),
                () -> assertThat(data.path("last").asBoolean()).isTrue()
            );
        }

        @DisplayName("해당 기간의 완료 Snapshot이 없으면 200 OK와 빈 Page를 반환한다")
        @Test
        void returnsEmptyPage_whenCompletedSnapshotDoesNotExist() throws Exception {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&period=WEEKLY",
                String.class
            );

            // assert
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.path("content")).isEmpty(),
                () -> assertThat(data.path("totalElements").asLong()).isZero(),
                () -> assertThat(data.path("totalPages").asInt()).isZero()
            );
        }

        @DisplayName("완료된 Empty Snapshot이면 200 OK와 빈 Page를 반환한다")
        @Test
        void returnsEmptyPage_whenCompletedSnapshotIsEmpty() throws Exception {
            // arrange
            insertCompletedSnapshot(RankingPeriod.MONTHLY, 1L);

            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&period=MONTHLY",
                String.class
            );

            // assert
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.path("content")).isEmpty(),
                () -> assertThat(data.path("totalElements").asLong()).isZero(),
                () -> assertThat(data.path("totalPages").asInt()).isZero()
            );
        }

        @DisplayName("날짜가 누락되면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenDateIsMissing() {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(ENDPOINT_RANKINGS, String.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("날짜가 yyyyMMdd 형식의 유효한 날짜가 아니면 400 Bad Request를 반환한다")
        @ValueSource(strings = {"2026-07-14", "20260230", "hello"})
        @ParameterizedTest
        void returnsBadRequest_whenDateIsInvalid(String date) {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS + "?date=" + date,
                String.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("Page 요청 범위가 유효하지 않으면 400 Bad Request를 반환한다")
        @ValueSource(strings = {"&page=-1", "&size=0"})
        @ParameterizedTest
        void returnsBadRequest_whenPageRequestIsInvalid(String query) {
            // act
            ResponseEntity<String> response = testRestTemplate.getForEntity(
                ENDPOINT_RANKINGS
                    + "?date=" + RANKING_DATE.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + query,
                String.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private Product createProduct(Brand brand, String name, long price) {
        Product product = productService.createProduct(
            brand.getId(),
            name,
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            price
        );
        productLikeSummaryWriter.initialize(product.getId(), product.getBrandId());
        return product;
    }

    private void addRanking(Product product, double score) {
        masterRedisTemplate.opsForZSet().add(
            RankingRedisKey.daily(RANKING_DATE),
            String.valueOf(product.getId()),
            score
        );
    }

    private void insertCompletedSnapshot(RankingPeriod period, long snapshotId) {
        jdbcTemplate.update(
            """
                insert into product_rank_snapshots(
                    id,
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                )
                values (?, ?, ?, ?, 1, 'V1', 0.1, 0.2, 0.7, 10000, now(6), now(6))
                """,
            snapshotId,
            period.name(),
            period.periodStart(RANKING_DATE),
            RANKING_DATE
        );
    }

    private void insertPublishedRank(
        RankingPeriod period,
        long snapshotId,
        long productId,
        int rank
    ) {
        String tableName = switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
        jdbcTemplate.update(
            "insert into " + tableName
                + "(snapshot_id, product_id, rank_no, score) values (?, ?, ?, 1.0)",
            snapshotId,
            productId,
            rank
        );
    }
}
