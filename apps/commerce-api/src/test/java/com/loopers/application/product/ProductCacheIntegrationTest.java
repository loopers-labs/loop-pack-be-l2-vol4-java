package com.loopers.application.product;

import com.loopers.application.like.ProductLikeFacade;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortType;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductLikeFacade productLikeFacade;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createProduct() {
        return productJpaRepository.save(new ProductModel("상품", "설명", 1000L, 10, 1L)).getId();
    }

    @DisplayName("상품 상세 캐시는,")
    @Nested
    class DetailCache {
        @DisplayName("최초 조회(미스) 시 DB 값을 반환하고 TTL 과 함께 캐시에 적재한다.")
        @Test
        void cachesDetailWithTtl_onFirstMiss() {
            // arrange
            Long productId = createProduct();
            String key = ProductCachePolicy.detailKey(productId);

            // act
            ProductInfo info = productFacade.getProduct(productId);

            // assert
            assertAll(
                () -> assertThat(info.id()).isEqualTo(productId),
                () -> assertThat(redisTemplate.hasKey(key)).isTrue(),
                () -> assertThat(redisTemplate.getExpire(key)).isPositive()
            );
        }

        @DisplayName("캐시 히트 시 DB 를 거치지 않고 캐시된 값을 반환한다.")
        @Test
        void returnsCachedValue_onHit() {
            // arrange — 조회로 캐시 적재 후, 캐시를 우회해 DB 값만 변경
            Long productId = createProduct();
            productFacade.getProduct(productId);
            productJpaRepository.incrementLikeCount(productId);

            // act
            ProductInfo info = productFacade.getProduct(productId);

            // assert — 캐시가 응답했으므로 변경 전 값(0)이 유지된다
            assertThat(info.likeCount()).isEqualTo(0L);
        }

        @DisplayName("상품 수정 시 캐시가 무효화되어, 다음 조회에 수정 값이 반영된다.")
        @Test
        void evictsCache_onProductUpdate() {
            // arrange
            Long productId = createProduct();
            productFacade.getProduct(productId);

            // act
            productFacade.updateProduct(productId, "수정된 상품", "수정된 설명", 2000L, 5, 2L);
            ProductInfo info = productFacade.getProduct(productId);

            // assert
            assertAll(
                () -> assertThat(info.name()).isEqualTo("수정된 상품"),
                () -> assertThat(info.price()).isEqualTo(2000L),
                () -> assertThat(info.brandId()).isEqualTo(2L)
            );
        }

        @DisplayName("좋아요 등록/취소 시 캐시가 무효화되어, 다음 조회에 likeCount 가 반영된다.")
        @Test
        void evictsCache_onLikeAndUnlike() {
            // arrange
            Long productId = createProduct();
            productFacade.getProduct(productId);

            // act & assert — 등록
            productLikeFacade.like("user-1", productId);
            assertThat(productFacade.getProduct(productId).likeCount()).isEqualTo(1L);

            // act & assert — 취소
            productLikeFacade.unlike("user-1", productId);
            assertThat(productFacade.getProduct(productId).likeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("상품 목록 캐시는,")
    @Nested
    class ListCache {
        @DisplayName("앞쪽 페이지(page < 5) 조회 시 캐시에 적재한다.")
        @Test
        void cachesList_whenPageIsCacheable() {
            // arrange
            createProduct();
            ProductSearchCondition condition = new ProductSearchCondition(null, ProductSortType.LIKES_DESC, 0, 20);

            // act
            ProductPageInfo pageInfo = productFacade.searchProducts(condition);

            // assert
            assertAll(
                () -> assertThat(pageInfo.totalCount()).isEqualTo(1),
                () -> assertThat(redisTemplate.hasKey(ProductCachePolicy.listKey(condition))).isTrue()
            );
        }

        @DisplayName("깊은 페이지(page >= 5) 조회는 캐시하지 않는다.")
        @Test
        void doesNotCacheList_whenPageIsDeep() {
            // arrange
            createProduct();
            ProductSearchCondition condition = new ProductSearchCondition(null, ProductSortType.LIKES_DESC, 5, 20);

            // act
            productFacade.searchProducts(condition);

            // assert
            assertThat(redisTemplate.hasKey(ProductCachePolicy.listKey(condition))).isFalse();
        }

        @DisplayName("캐시 히트 시 캐시된 목록을 반환한다.")
        @Test
        void returnsCachedList_onHit() {
            // arrange — 목록 캐시 적재 후 DB 에만 상품 추가
            createProduct();
            ProductSearchCondition condition = new ProductSearchCondition(null, ProductSortType.LIKES_DESC, 0, 20);
            productFacade.searchProducts(condition);
            createProduct();

            // act
            ProductPageInfo pageInfo = productFacade.searchProducts(condition);

            // assert — 캐시가 응답했으므로 추가 전 건수(1)가 유지된다
            assertThat(pageInfo.totalCount()).isEqualTo(1);
        }
    }
}
