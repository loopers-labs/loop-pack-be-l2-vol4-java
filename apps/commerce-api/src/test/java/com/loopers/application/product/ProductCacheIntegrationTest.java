package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 캐시 통합 테스트.
 *
 * <p>read-through, 무효화(evict), 재고 실시간 조회 세 가지 정책을 검증한다.
 * Testcontainers Redis + DB 를 사용하며, @AfterEach 에서 상태를 초기화한다.
 */
@SpringBootTest
@Testcontainers
class ProductCacheIntegrationTest {

    @Container
    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        String port = String.valueOf(redisContainer.getMappedPort(6379));
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
    }

    @Autowired private ProductApplicationService productApplicationService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    // ===== 상세 캐시 =====

    @DisplayName("상품 상세 첫 조회 시 캐시 미스 → Redis에 키가 저장된다.")
    @Test
    void getProductDetail_storesInCache_onCacheMiss() {
        ProductModel product = givenProductWithStock(10);

        productApplicationService.getProductDetail(product.getId());

        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();
    }

    @DisplayName("재고는 캐시 히트 시에도 항상 DB에서 실시간 조회된다.")
    @Test
    void getProductDetail_alwaysFetchesStockFromDb_evenOnCacheHit() {
        ProductModel product = givenProductWithStock(8);
        productApplicationService.getProductDetail(product.getId()); // 캐시 저장 (재고=8, remainingStock=8)

        // 캐시 무효화 없이 DB 재고만 직접 변경
        StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        stock.changeQuantity(5);
        stockRepository.save(stock);

        // 캐시 히트 상태에서 재조회
        ProductInfo result = productApplicationService.getProductDetail(product.getId());

        // 재고는 캐시가 아닌 DB 기준 — 5로 반영되어야 한다 (둘 다 ≤10 표시 구간이므로 remainingStock 에 나타남)
        assertThat(result.remainingStock()).isEqualTo(5);
    }

    @DisplayName("재고 30개 → 10개 이하로 줄어도 inStock 상태가 실시간 반영된다.")
    @Test
    void getProductDetail_reflectsStockAvailability_inRealtime() {
        ProductModel product = givenProductWithStock(1); // 재고 1개
        productApplicationService.getProductDetail(product.getId()); // 캐시 저장 (inStock=true)

        StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        stock.changeQuantity(0); // 품절로 변경
        stockRepository.save(stock);

        ProductInfo result = productApplicationService.getProductDetail(product.getId());

        assertThat(result.inStock()).isFalse(); // 캐시 히트에도 DB 재고 기준
    }

    @DisplayName("상품 수정 후 상세 캐시가 무효화된다.")
    @Test
    void updateProduct_evictsDetailCache() {
        ProductModel product = givenProductWithStock(10);
        productApplicationService.getProductDetail(product.getId());
        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();

        productApplicationService.updateProduct(product.getId(), "에어맥스2", "신형", 60_000L, null);

        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isFalse();
    }

    @DisplayName("상품 수정 후 재조회 시 최신 정보가 캐시에 다시 저장된다.")
    @Test
    void updateProduct_recachesUpdatedInfo_onNextFetch() {
        ProductModel product = givenProductWithStock(10);
        productApplicationService.updateProduct(product.getId(), "에어맥스2", "신형", 60_000L, null);

        ProductInfo result = productApplicationService.getProductDetail(product.getId());

        assertThat(result.name()).isEqualTo("에어맥스2");
        assertThat(result.price()).isEqualTo(60_000L);
        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();
    }

    @DisplayName("상품 삭제 후 상세 캐시가 무효화된다.")
    @Test
    void deleteProduct_evictsDetailCache() {
        ProductModel product = givenProductWithStock(10);
        productApplicationService.getProductDetail(product.getId());
        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();

        productApplicationService.deleteProduct(product.getId());

        assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isFalse();
    }

    // ===== 목록 캐시 =====

    @DisplayName("상품 목록 첫 조회 시 캐시 미스 → Redis에 키가 저장된다.")
    @Test
    void getProducts_storesInCache_onCacheMiss() {
        givenProductWithStock(10);

        productApplicationService.getProducts(null, "latest", 0, 20);

        Set<String> keys = redisTemplate.keys("product:list:*");
        assertThat(keys).isNotNull().isNotEmpty();
    }

    @DisplayName("상품 수정 후 목록 캐시 전체가 무효화된다.")
    @Test
    void updateProduct_evictsAllListCache() {
        ProductModel product = givenProductWithStock(10);
        productApplicationService.getProducts(null, "latest", 0, 20);
        assertThat(redisTemplate.keys("product:list:*")).isNotEmpty();

        productApplicationService.updateProduct(product.getId(), "변경상품", "설명", 10_000L, null);

        Set<String> keys = redisTemplate.keys("product:list:*");
        assertThat(keys).isNullOrEmpty();
    }

    @DisplayName("상품 삭제 후 목록 캐시 전체가 무효화된다.")
    @Test
    void deleteProduct_evictsAllListCache() {
        ProductModel product = givenProductWithStock(10);
        productApplicationService.getProducts(null, "latest", 0, 20);

        productApplicationService.deleteProduct(product.getId());

        Set<String> keys = redisTemplate.keys("product:list:*");
        assertThat(keys).isNullOrEmpty();
    }

    @DisplayName("신규 상품 등록 후 목록 캐시는 즉시 무효화되지 않는다 — TTL 만료 대기.")
    @Test
    void createProduct_doesNotEvictListCache() {
        givenProductWithStock(10);
        productApplicationService.getProducts(null, "latest", 0, 20);
        assertThat(redisTemplate.keys("product:list:*")).isNotEmpty();

        BrandModel brand = brandRepository.save(new BrandModel("아디다스", "스포츠"));
        productApplicationService.createProduct(brand.getId(), "울트라부스트", "러닝화", 80_000L, 5);

        // 신규 등록 후에도 기존 목록 캐시는 살아있어야 한다
        assertThat(redisTemplate.keys("product:list:*")).isNotEmpty();
    }

    // ===== helper =====

    private ProductModel givenProductWithStock(int qty) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), qty));
        return product;
    }
}
