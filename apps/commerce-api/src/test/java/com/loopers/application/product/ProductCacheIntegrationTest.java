package com.loopers.application.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.config.CacheConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOption;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private LikeFacade likeFacade;

    @MockitoSpyBean
    private ProductRepository productRepository;   // DB 호출 횟수를 세기 위해 스파이

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void clearCache() {
        // Redis 캐시는 DB 와 별개라 테스트마다 직접 비워준다 (이전 테스트 잔재 방지)
        cacheManager.getCache(CacheConfig.PRODUCT_DETAIL).clear();
        cacheManager.getCache(CacheConfig.PRODUCT_LIST).clear();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCache(CacheConfig.PRODUCT_DETAIL).clear();
        cacheManager.getCache(CacheConfig.PRODUCT_LIST).clear();
    }

    private ProductModel givenProduct(Long brandId, int likeUnusedStock) {
        return productRepository.save(
                new ProductModel(brandId, "상품A", "설명", Money.of(1000L), Quantity.of(likeUnusedStock), "img.jpg"));
    }

    @DisplayName("상품 상세를 두 번 조회하면, 두 번째는 캐시 히트라 DB 조회는 한 번만 일어난다.")
    @Test
    void detail_secondCall_hitsCache_dbCalledOnce() {
        // arrange
        BrandModel brand = brandRepository.save(new BrandModel("브랜드A", "브랜드설명"));
        ProductModel product = givenProduct(brand.getId(), 10);

        // act
        ProductInfo first = productFacade.getProductDetail(product.getId());
        ProductInfo second = productFacade.getProductDetail(product.getId());

        // assert
        verify(productRepository, times(1)).findById(product.getId());   // 2번 호출했지만 DB는 1번
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(second.likeCount()).isEqualTo(0);
    }

    @DisplayName("좋아요를 누르면 해당 상품의 상세 캐시가 무효화되어, 다음 조회 시 갱신된 좋아요 수가 반영된다.")
    @Test
    void detail_afterLike_isEvicted_andRefreshed() {
        // arrange
        BrandModel brand = brandRepository.save(new BrandModel("브랜드A", "브랜드설명"));
        ProductModel product = givenProduct(brand.getId(), 10);
        ProductInfo before = productFacade.getProductDetail(product.getId());   // likeCount=0 캐시에 적재
        assertThat(before.likeCount()).isEqualTo(0);

        // act
        likeFacade.like(999L, product.getId());                                 // 캐시 evict
        ProductInfo after = productFacade.getProductDetail(product.getId());    // 다시 조회 → DB 재조회

        // assert
        assertThat(after.likeCount()).isEqualTo(1);   // 무효화 안 됐다면 여전히 0 이어야 함
    }

    @DisplayName("상품 목록을 두 번 조회하면, 두 번째는 캐시 히트라 DB 조회는 한 번만 일어난다.")
    @Test
    void list_secondCall_hitsCache_dbCalledOnce() {
        // arrange
        BrandModel brand = brandRepository.save(new BrandModel("브랜드A", "브랜드설명"));
        givenProduct(brand.getId(), 10);

        // act
        productFacade.getProductList(brand.getId(), ProductSortOption.LIKES_DESC, 0, 20);
        productFacade.getProductList(brand.getId(), ProductSortOption.LIKES_DESC, 0, 20);

        // assert
        verify(productRepository, times(1))
                .findAll(brand.getId(), ProductSortOption.LIKES_DESC, 0, 20);
    }
}
