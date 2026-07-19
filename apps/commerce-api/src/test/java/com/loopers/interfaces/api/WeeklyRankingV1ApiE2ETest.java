package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 주간 랭킹은 배치가 적재한 MV(mv_product_rank_weekly)에서 조회한다.
 * commerce-api 는 MV 를 읽기만 하므로 엔티티가 없어 테이블이 자동 생성되지 않는다 → 테스트에서 직접 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeeklyRankingV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public WeeklyRankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mv_product_rank_weekly ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY, snapshot_date DATE NOT NULL, rank_no INT NOT NULL, "
            + "product_id BIGINT NOT NULL, score DOUBLE NOT NULL, "
            + "UNIQUE KEY uk_mv_weekly_snapshot_product (snapshot_date, product_id))");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
    }

    private Product saveProduct(String name) {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product(name, "설명",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    private void seedWeeklyRank(LocalDate snapshotDate, int rank, long productId, double score) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly (snapshot_date, rank_no, product_id, score) VALUES (?,?,?,?)",
            snapshotDate, rank, productId, score);
    }

    private ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> get(String url) {
        ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY")
    @Nested
    class Weekly {

        @DisplayName("주간 MV의 순위대로 상품 랭킹 페이지를 반환한다.")
        @Test
        void returnsWeeklyRankingFromMv() {
            Product first = saveProduct("1등 상품");
            Product second = saveProduct("2등 상품");
            LocalDate today = LocalDate.now();
            seedWeeklyRank(today, 1, first.getId(), 88.0);
            seedWeeklyRank(today, 2, second.getId(), 40.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get("/api/v1/rankings?period=WEEKLY&page=1&size=20");

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(2),
                () -> assertThat(response.getBody().data().items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().items().get(0).product().id()).isEqualTo(first.getId()),
                () -> assertThat(response.getBody().data().items().get(1).product().id()).isEqualTo(second.getId()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L)
            );
        }

        @DisplayName("요청일의 스냅샷이 없으면, 그 이전의 가장 최근 스냅샷으로 폴백한다.")
        @Test
        void fallsBackToLatestSnapshot_whenRequestedDateMissing() {
            Product product = saveProduct("지난 스냅샷 상품");
            // 오늘 스냅샷은 없고 3일 전 스냅샷만 있다.
            seedWeeklyRank(LocalDate.now().minusDays(3), 1, product.getId(), 77.0);

            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                get("/api/v1/rankings?period=WEEKLY");

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).product().id()).isEqualTo(product.getId())
            );
        }
    }
}
