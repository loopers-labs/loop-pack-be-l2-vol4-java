package com.loopers.application.product;

import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.product.ProductSortType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis 장애 시 graceful degradation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductCacheDegradationTest {

    // Redis 를 아무도 listen 하지 않는 포트로 가리켜 '죽은 Redis' 를 시뮬레이션한다(모든 캐시 연산이 연결 실패).
    @DynamicPropertySource
    static void deadRedis(DynamicPropertyRegistry registry) {
        registry.add("datasource.redis.master.host", () -> "localhost");
        registry.add("datasource.redis.master.port", () -> 6399);
        registry.add("datasource.redis.replicas[0].host", () -> "localhost");
        registry.add("datasource.redis.replicas[0].port", () -> 6399);
    }

    private final ProductFacade productFacade;
    private final BrandFacade brandFacade;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductCacheDegradationTest(
        ProductFacade productFacade,
        BrandFacade brandFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productFacade = productFacade;
        this.brandFacade = brandFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandFacade.create("나이키", "Just Do It").id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // Redis 가 죽어 있어 flush 불가/불필요
    }

    @DisplayName("Redis 가 죽어도 상품 상세 조회는 DB 로 폴백해 정상 응답한다(예외 전파 없음).")
    @Test
    void getProductFallsBackToDb_whenRedisIsDown() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();

        // when
        ProductInfo info = productFacade.getProduct(productId);   // 캐시 연산 실패 → 핸들러가 삼킴 → DB 폴백 (throw 하면 degradation 실패)

        // then
        assertThat(info.name()).isEqualTo("에어맥스 270");
        assertThat(info.likeCount()).isZero();
    }

    @DisplayName("Redis 가 죽어도 상품 목록 조회는 DB 로 폴백해 정상 응답한다(예외 전파 없음).")
    @Test
    void getAllProductsFallsBackToDb_whenRedisIsDown() {
        // given
        productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);

        // when
        List<ProductInfo> result = productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);

        // then
        assertThat(result).singleElement().extracting(ProductInfo::name).isEqualTo("에어맥스 270");
    }
}
