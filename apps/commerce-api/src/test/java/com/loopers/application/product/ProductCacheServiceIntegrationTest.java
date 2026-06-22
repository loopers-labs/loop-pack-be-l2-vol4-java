package com.loopers.application.product;

import com.loopers.domain.product.ProductSort;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheServiceIntegrationTest {

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 상세 캐시를 조회할 때,")
    @Nested
    class GetDetail {

        @DisplayName("캐시가 없으면, Optional.empty()를 반환한다.")
        @Test
        void getDetail_returnEmpty_whenCacheMiss() {
            // act
            Optional<ProductCacheItem> result = productCacheService.getDetail(1L);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("putDetail 후 동일 ID로 조회하면, 저장한 값을 반환한다.")
        @Test
        void getDetail_returnCachedItem_afterPut() {
            // arrange
            ProductCacheItem item = new ProductCacheItem(1L, "에어포스1", 1L, "나이키", 5);
            productCacheService.putDetail(1L, item);

            // act
            Optional<ProductCacheItem> result = productCacheService.getDetail(1L);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("에어포스1");
            assertThat(result.get().brandName()).isEqualTo("나이키");
            assertThat(result.get().likeCount()).isEqualTo(5);
        }

        @DisplayName("evictDetail 후 조회하면, Optional.empty()를 반환한다.")
        @Test
        void evictDetail_removesKey() {
            // arrange
            ProductCacheItem item = new ProductCacheItem(1L, "에어포스1", 1L, "나이키", 0);
            productCacheService.putDetail(1L, item);

            // act
            productCacheService.evictDetail(1L);

            // assert
            assertThat(productCacheService.getDetail(1L)).isEmpty();
        }
    }

    @DisplayName("상품 목록 캐시를 조회할 때,")
    @Nested
    class GetList {

        @DisplayName("캐시가 없으면, Optional.empty()를 반환한다.")
        @Test
        void getList_returnEmpty_whenCacheMiss() {
            // act
            Optional<List<ProductInfo>> result = productCacheService.getList(ProductSort.LATEST, 0);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("putList 후 동일 sort/page로 조회하면, 저장한 값을 반환한다.")
        @Test
        void getList_returnCachedList_afterPut() {
            // arrange
            List<ProductInfo> items = List.of(
                new ProductInfo(1L, "에어포스1", 139000L, 1L, null, 0, null)
            );
            productCacheService.putList(ProductSort.LATEST, 0, items);

            // act
            Optional<List<ProductInfo>> result = productCacheService.getList(ProductSort.LATEST, 0);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(1);
            assertThat(result.get().get(0).name()).isEqualTo("에어포스1");
            assertThat(result.get().get(0).price()).isEqualTo(139000L);
        }

        @DisplayName("evictAllList 호출 시, sort × page 9개 키가 모두 삭제된다.")
        @Test
        void evictAllList_removesAllNineKeys() {
            // arrange — 3 sorts × 3 pages = 9개 키 저장
            List<ProductInfo> items = List.of(
                new ProductInfo(1L, "에어포스1", 139000L, 1L, null, 0, null)
            );
            for (ProductSort sort : ProductSort.values()) {
                for (int page = 0; page < 3; page++) {
                    productCacheService.putList(sort, page, items);
                }
            }

            // act
            productCacheService.evictAllList();

            // assert
            for (ProductSort sort : ProductSort.values()) {
                for (int page = 0; page < 3; page++) {
                    assertThat(productCacheService.getList(sort, page)).isEmpty();
                }
            }
        }
    }
}
