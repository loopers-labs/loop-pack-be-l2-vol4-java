package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String RANKINGS = "/api/v1/rankings";
    private static final LocalDate TODAY = LocalDate.now(RankingKeys.ZONE);

    private final TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long brandId;
    private Long product1Id;
    private Long product2Id;
    private Long product3Id;

    @Autowired
    public RankingV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandRepository brandRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandRepository.save(Brand.create("루퍼스", "테스트 브랜드")).getId();
        product1Id = createProduct("상품1", 10_000L);
        product2Id = createProduct("상품2", 20_000L);
        product3Id = createProduct("상품3", 30_000L);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createProduct(String name, long price) {
        Product product = productRepository.save(Product.create(brandId, name, Money.of(price)));
        inventoryRepository.save(Inventory.create(product.getId(), 10));
        return product.getId();
    }

    private void seed(LocalDate date, Long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKeys.display(date), String.valueOf(productId), score);
    }

    @DisplayName("랭킹 페이지 조회 시 점수 내림차순으로, 상품 정보가 조립되어 반환된다")
    @Test
    void getRankings_returnsAggregatedProductInfoInScoreOrder() {
        seed(TODAY, product1Id, 1.0);
        seed(TODAY, product2Id, 3.0);
        seed(TODAY, product3Id, 2.0);

        ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = get(RANKINGS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RankingV1Dto.PageResponse body = response.getBody().data();
        assertThat(body.totalElements()).isEqualTo(3);
        assertThat(body.content()).hasSize(3);
        assertThat(body.content().get(0).productId()).isEqualTo(product2Id);
        assertThat(body.content().get(0).rank()).isEqualTo(1);
        assertThat(body.content().get(0).name()).isEqualTo("상품2");
        assertThat(body.content().get(0).brandName()).isEqualTo("루퍼스");
        assertThat(body.content().get(0).price()).isEqualTo(20_000L);
        assertThat(body.content().get(1).productId()).isEqualTo(product3Id);
        assertThat(body.content().get(2).productId()).isEqualTo(product1Id);
    }

    @DisplayName("2페이지 조회 시 순위가 offset 을 이어받는다")
    @Test
    void getRankings_paginatesWithContinuedRank() {
        seed(TODAY, product1Id, 3.0);
        seed(TODAY, product2Id, 2.0);
        seed(TODAY, product3Id, 1.0);

        ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = get(RANKINGS + "?page=1&size=2");

        RankingV1Dto.PageResponse body = response.getBody().data();
        assertThat(body.content()).hasSize(1);
        assertThat(body.content().get(0).productId()).isEqualTo(product3Id);
        assertThat(body.content().get(0).rank()).isEqualTo(3);
        assertThat(body.hasNext()).isFalse();
    }

    @DisplayName("date 파라미터로 이전 날짜의 랭킹을 조회할 수 있다")
    @Test
    void getRankings_supportsPastDate() {
        LocalDate yesterday = TODAY.minusDays(1);
        seed(yesterday, product1Id, 5.0);

        ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response =
                get(RANKINGS + "?date=" + yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));

        RankingV1Dto.PageResponse body = response.getBody().data();
        assertThat(body.content()).hasSize(1);
        assertThat(body.content().get(0).productId()).isEqualTo(product1Id);
    }

    @DisplayName("삭제된 상품은 응답에서 제외되고 순위 gap 이 유지된다")
    @Test
    void getRankings_excludesDeletedProductKeepingRankGap() {
        seed(TODAY, product1Id, 3.0);
        seed(TODAY, product2Id, 2.0); // 삭제 예정 — 보드에는 남는다
        seed(TODAY, product3Id, 1.0);
        Product product2 = productRepository.find(product2Id).orElseThrow();
        product2.delete();
        productRepository.update(product2);

        ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = get(RANKINGS);

        RankingV1Dto.PageResponse body = response.getBody().data();
        assertThat(body.content()).hasSize(2);
        assertThat(body.content().get(0).rank()).isEqualTo(1);
        assertThat(body.content().get(1).productId()).isEqualTo(product3Id);
        assertThat(body.content().get(1).rank()).isEqualTo(3); // 2위 자리는 gap
    }

    @DisplayName("보드가 없는 날짜는 빈 목록을 반환한다")
    @Test
    void getRankings_emptyBoardReturnsEmptyContent() {
        ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = get(RANKINGS + "?date=20300101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @DisplayName("상품 상세 조회 시 오늘 순위가 함께 반환되고, 순위에 없다면 null 이다")
    @Test
    void getProductDetail_includesTodayRankOrNull() {
        seed(TODAY, product1Id, 5.0);

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> ranked = testRestTemplate.exchange(
                "/api/v1/products/" + product1Id, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> unranked = testRestTemplate.exchange(
                "/api/v1/products/" + product2Id, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(ranked.getBody().data().rank()).isEqualTo(1L);
        assertThat(unranked.getBody().data().rank()).isNull();
    }

    private ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> get(String url) {
        return testRestTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
        });
    }
}
