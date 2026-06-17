package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 조회 Redis 캐시의 적중·무효화 동작 검증.
 * - 캐시 적중: 캐싱 후 DB를 우회로 바꿔도(도메인 서비스로 직접 변경 → Facade의 evict 안 탐) 조회는 캐시값을 낸다.
 * - 무효화: Facade.updateProduct/createProduct는 캐시를 evict하므로 이후 조회가 최신값을 낸다.
 */
@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired ProductFacade productFacade;
    @Autowired ProductService productService;
    @Autowired BrandService brandService;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    private Long brandId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createProduct(String name, long price, int stock) {
        if (brandId == null) brandId = brandService.register("나이키", "스포츠").getId();
        return productFacade.createProduct(brandId, name, "설명", null, price, stock).id();
    }

    @DisplayName("상세를 캐싱한 뒤 DB를 우회로 변경해도, 조회는 캐시값(옛 가격)을 낸다")
    @Test
    void detail_servesCachedValue_whenDbChangedBypassingFacade() {
        Long id = createProduct("에어맥스", 100_000L, 10);
        productFacade.getProductDetail(id, null);                 // 캐시 적재

        // Facade를 거치지 않고(=evict 없이) 도메인 서비스로 직접 가격 변경
        productService.updateProduct(id, "에어맥스", "설명", null, 222_000L);

        ProductDetailInfo again = productFacade.getProductDetail(id, null);
        assertThat(again.price()).isEqualTo(100_000L);            // 캐시 적중 → 옛값
    }

    @DisplayName("Facade.updateProduct는 상세 캐시를 무효화하여 이후 조회가 최신값을 낸다")
    @Test
    void detail_evictedAndFresh_afterFacadeUpdate() {
        Long id = createProduct("에어맥스", 100_000L, 10);
        productFacade.getProductDetail(id, null);                 // 캐시 적재

        productFacade.updateProduct(id, "에어맥스", "설명", null, 333_000L, 10);  // evict

        ProductDetailInfo again = productFacade.getProductDetail(id, null);
        assertThat(again.price()).isEqualTo(333_000L);            // 무효화 → 최신값
    }

    @DisplayName("목록을 캐싱한 뒤 새 상품을 등록하면 목록 캐시가 무효화되어 새 상품이 포함된다")
    @Test
    void list_evicted_afterCreateProduct() {
        createProduct("상품A", 100_000L, 10);
        List<ProductListItemInfo> first = productFacade.getProducts(null, ProductSortType.LATEST, 0, 20, null);
        int before = first.size();

        createProduct("상품B", 100_000L, 10);                      // evictListForNewProduct

        List<ProductListItemInfo> second = productFacade.getProducts(null, ProductSortType.LATEST, 0, 20, null);
        assertThat(second).hasSize(before + 1);                    // 무효화 → 새 상품 반영
    }
}
