package com.loopers.application.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductListCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductListCacheRepository productListCacheRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long brandId;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("나이키"));
        brandId = brand.getId();
        for (int i = 0; i < 3; i++) {
            Product product = productRepository.save(Product.create(brandId, "상품" + i, "설명" + i, 10_000L * (i + 1)));
            stockRepository.save(Stock.create(product.getId(), 100));
        }
    }

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("product:list:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 목록을 처음 조회하면(miss), DB에서 읽고 결과가 캐시에 저장된다.")
    @Test
    void cachesList_onCacheMiss() {
        // Arrange: 캐시가 비어 있음
        assertThat(productListCacheRepository.find(brandId, "latest", 0, 20)).isEmpty();

        // Act: 첫 조회 (miss → DB → 캐시 저장)
        Page<ProductInfo> result = productFacade.getProducts(brandId, "latest", 0, 20);

        // Assert: 결과 정상 + 캐시에 저장됨
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(productListCacheRepository.find(brandId, "latest", 0, 20)).isPresent();
    }

    @DisplayName("목록 캐시에 값이 있으면(hit), DB가 아닌 캐시에서 읽는다.")
    @Test
    void readsFromCache_onCacheHit() {
        // Arrange: DB(3건)와 다른 가짜 목록을 캐시에 직접 심어둠
        ProductInfo cachedItem = new ProductInfo(
            999L, brandId, "캐시에서_온_상품", "캐시설명", 1_000L, 0,
            "캐시브랜드", true, 5, ZonedDateTime.now(), ZonedDateTime.now()
        );
        productListCacheRepository.save(brandId, "latest", 0, 20,
            new ProductListCache(List.of(cachedItem), 1, 0, 20));

        // Act
        Page<ProductInfo> result = productFacade.getProducts(brandId, "latest", 0, 20);

        // Assert: DB의 3건이 아니라 캐시의 1건이 반환됨 → 캐시에서 읽었다는 증명
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("캐시에서_온_상품");
    }

    @DisplayName("조회 조건(정렬)이 다르면 다른 캐시 키를 사용한다.")
    @Test
    void usesDifferentKey_forDifferentCondition() {
        // Arrange: latest 정렬만 캐시에 적재
        productFacade.getProducts(brandId, "latest", 0, 20);

        // Act & Assert: price_asc는 다른 키라 아직 캐시에 없음(miss)
        assertThat(productListCacheRepository.find(brandId, "latest", 0, 20)).isPresent();
        assertThat(productListCacheRepository.find(brandId, "price_asc", 0, 20)).isEmpty();
    }
}
