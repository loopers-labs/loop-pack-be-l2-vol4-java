package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.cache.ProductCacheService;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class ProductCacheServiceTest {

    @Autowired private ProductApplicationService productApplicationService;
    @Autowired private ProductCacheService productCacheService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private BrandEntity savedBrand() {
        return brandJpaRepository.save(BrandEntity.from(new BrandModel("나이키", "스포츠 브랜드")));
    }

    private ProductEntity savedProduct(BrandEntity brand) {
        return productJpaRepository.save(ProductEntity.from(
            new ProductModel(brand.getId(), "에어맥스", "운동화", 100_000L, 10),
            brand
        ));
    }

    @DisplayName("상품 캐시 세분화 - ")
    @Nested
    class ProductCache {

        @DisplayName("최초 조회 시 캐시 미스로 DB를 조회하고 meta/price/stock/likeCount/brand 모두 캐시에 저장한다.")
        @Test
        void cacheMiss_populatesAllKeys() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);
            Long productId = product.getId();

            assertThat(productCacheService.getProduct(productId)).isEmpty();

            productApplicationService.getProduct(productId);

            assertThat(productCacheService.getProduct(productId)).isPresent();
            assertThat(productCacheService.getProductLikeCount(productId)).isPresent();
            assertThat(productCacheService.getBrand(brand.getId())).isPresent();
        }

        @DisplayName("캐시 히트 시 DB 없이 응답을 반환하고 Hibernate 쿼리가 발생하지 않는다.")
        @Test
        void cacheHit_returnsFromCache() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);
            Long productId = product.getId();

            // 캐시 워밍
            productApplicationService.getProduct(productId);
            assertThat(productCacheService.getProduct(productId)).isPresent();

            // DB에서 상품 삭제 (캐시만 남은 상태 시뮬레이션)
            productJpaRepository.deleteById(productId);

            // 캐시에서 응답
            ProductInfo result = productApplicationService.getProduct(productId);
            assertThat(result.id()).isEqualTo(productId);
            assertThat(result.name()).isEqualTo("에어맥스");
            assertThat(result.price()).isEqualTo(100_000L);
        }

        @DisplayName("상품 수정 시 meta/price/stock 캐시가 모두 무효화된다.")
        @Test
        void evictsProductCache_onUpdate() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);
            Long productId = product.getId();

            productApplicationService.getProduct(productId);
            assertThat(productCacheService.getProduct(productId)).isPresent();

            productApplicationService.updateProduct(productId, "에어맥스V2", "신형 운동화", 120_000L, 20);

            assertThat(productCacheService.getProduct(productId)).isEmpty();
        }

        @DisplayName("상품 삭제 시 meta/price/stock 캐시가 모두 무효화된다.")
        @Test
        void evictsProductCache_onDelete() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);
            Long productId = product.getId();

            productApplicationService.getProduct(productId);
            assertThat(productCacheService.getProduct(productId)).isPresent();

            productApplicationService.deleteProduct(productId);

            assertThat(productCacheService.getProduct(productId)).isEmpty();
        }
    }

    @DisplayName("LikeCount 캐시 - ")
    @Nested
    class LikeCountCache {

        @DisplayName("likeCount는 조회 시 캐시에 저장되고 TTL 만료 전까지 재사용된다.")
        @Test
        void likeCount_isCachedOnFirstRequest() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);
            Long productId = product.getId();

            assertThat(productCacheService.getProductLikeCount(productId)).isEmpty();

            productApplicationService.getProduct(productId);

            assertThat(productCacheService.getProductLikeCount(productId)).isPresent();
            assertThat(productCacheService.getProductLikeCount(productId).get()).isEqualTo(0L);
        }

        @DisplayName("likeCount 캐시는 직접 evict 메서드가 없고 TTL(2분)에만 의존한다.")
        @Test
        void likeCount_hasNoEvictMethod() throws NoSuchMethodException {
            // ProductCacheService에 evictProductLikeCount 메서드가 없어야 한다
            boolean hasEvictMethod = false;
            try {
                ProductCacheService.class.getMethod("evictProductLikeCount", Long.class);
                hasEvictMethod = true;
            } catch (NoSuchMethodException ignored) {}

            assertThat(hasEvictMethod).isFalse();
        }
    }

    @DisplayName("Brand 캐시 - ")
    @Nested
    class BrandCache {

        @DisplayName("최초 상품 조회 시 브랜드도 캐시에 저장된다.")
        @Test
        void brand_isCachedOnFirstProductRequest() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);

            assertThat(productCacheService.getBrand(brand.getId())).isEmpty();

            productApplicationService.getProduct(product.getId());

            Optional<BrandModel> cachedBrand = productCacheService.getBrand(brand.getId());
            assertThat(cachedBrand).isPresent();
            assertThat(cachedBrand.get().getName()).isEqualTo("나이키");
        }
    }
}
