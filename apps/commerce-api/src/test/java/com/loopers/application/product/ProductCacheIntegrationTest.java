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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductCacheRepository productCacheRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("나이키"));
        Product product = productRepository.save(Product.create(brand.getId(), "에어맥스", "운동화", 100_000L));
        stockRepository.save(Stock.create(product.getId(), 100));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        productCacheRepository.evict(productId);
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 상세를 처음 조회하면(miss), DB에서 읽고 캐시에 저장된다.")
    @Test
    void cachesProduct_onCacheMiss() {
        // Arrange: 캐시가 비어 있음
        assertThat(productCacheRepository.find(productId)).isEmpty();

        // Act: 첫 조회 (cache miss → DB 조회 → 캐시 저장)
        ProductInfo result = productFacade.getProduct(productId);

        // Assert: 결과가 정상이고, 캐시에 저장됨 (ZonedDateTime 직렬화/역직렬화도 함께 검증)
        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("에어맥스");
        assertThat(productCacheRepository.find(productId)).isPresent();
    }

    @DisplayName("캐시에 값이 있으면(hit), 정적 정보는 캐시에서 읽고 재고는 실시간 DB에서 합성한다.")
    @Test
    void readsStaticFromCache_butStockFromDb_onCacheHit() {
        // Arrange: DB와 다른 정적 정보를 캐시에 직접 심어둠 (캐시에는 재고가 없음)
        ProductDetailCache cached = new ProductDetailCache(
            productId, 999L, "캐시에서_온_이름", "캐시설명", 55_000L, 7,
            "캐시브랜드", ZonedDateTime.now(), ZonedDateTime.now()
        );
        productCacheRepository.save(productId, cached);

        // Act
        ProductInfo result = productFacade.getProduct(productId);

        // Assert: 정적 정보는 캐시값 → 캐시에서 읽었다는 증명
        assertThat(result.name()).isEqualTo("캐시에서_온_이름");
        assertThat(result.brandName()).isEqualTo("캐시브랜드");
        // 재고는 캐시가 아니라 실시간 DB값(100) → 재고가 캐시에서 분리됐다는 증명
        assertThat(result.stockQuantity()).isEqualTo(100);
        assertThat(result.inStock()).isTrue();
    }

    @DisplayName("정적 정보가 캐시된 뒤 재고가 차감되어도, 상세 조회 시 최신 재고가 반영된다.")
    @Test
    void reflectsLatestStock_evenWhenStaticInfoCached() {
        // Arrange: 첫 조회로 정적 정보를 캐시에 적재 (재고 100)
        ProductInfo first = productFacade.getProduct(productId);
        assertThat(first.stockQuantity()).isEqualTo(100);
        assertThat(productCacheRepository.find(productId)).isPresent();

        // Act: 캐시 무효화 없이 재고만 차감 (주문 시 재고 차감을 모사)
        transactionTemplate.executeWithoutResult(status ->
            stockRepository.deductStock(productId, 30)
        );
        ProductInfo second = productFacade.getProduct(productId);

        // Assert: 정적 캐시는 그대로 살아있지만, 재고는 최신값(70)으로 반영됨
        assertThat(productCacheRepository.find(productId)).isPresent();
        assertThat(second.stockQuantity()).isEqualTo(70);
    }

    @DisplayName("상품을 수정하면, 해당 상품 캐시가 무효화된다.")
    @Test
    void evictsCache_onUpdate() {
        // Arrange: 한 번 조회해서 캐시에 저장
        productFacade.getProduct(productId);
        assertThat(productCacheRepository.find(productId)).isPresent();

        // Act: 상품 수정
        productFacade.updateProduct(productId, "수정된이름", "수정된설명", 200_000L);

        // Assert: 캐시가 비워짐 (다음 조회 때 새 값으로 다시 캐싱됨)
        assertThat(productCacheRepository.find(productId)).isEmpty();
    }
}
