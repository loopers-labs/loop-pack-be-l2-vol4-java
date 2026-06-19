package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheStoreIntegrationTest {

    @Autowired
    private ProductCacheStore productCacheStore;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private ProductInfo sampleProductInfo(Long id) {
        return new ProductInfo(id, "Nike", "에어맥스", BigDecimal.valueOf(150000), 0L, true);
    }

    @DisplayName("상품 상세 캐시를 다룰 때,")
    @Nested
    class ProductCache {

        @DisplayName("저장한 값이 있으면 히트로 조회된다.")
        @Test
        void returnsCachedValue_whenProductIsCached() {
            // given
            ProductInfo info = sampleProductInfo(1L);
            productCacheStore.putProduct(1L, info);

            // when
            Optional<ProductInfo> result = productCacheStore.findProduct(1L);

            // then
            assertThat(result).contains(info);
        }

        @DisplayName("삭제하면 더 이상 조회되지 않는다.")
        @Test
        void returnsEmpty_afterEviction() {
            // given
            productCacheStore.putProduct(2L, sampleProductInfo(2L));

            // when
            productCacheStore.evictProduct(2L);

            // then
            assertThat(productCacheStore.findProduct(2L)).isEmpty();
        }

        @DisplayName("evictAll로 여러 건을 한 번에 삭제하면 모두 조회되지 않는다.")
        @Test
        void evictsAllGivenProducts_whenEvictAllIsCalled() {
            // given
            productCacheStore.putProduct(3L, sampleProductInfo(3L));
            productCacheStore.putProduct(4L, sampleProductInfo(4L));

            // when
            productCacheStore.evictAll(List.of(3L, 4L));

            // then
            assertThat(productCacheStore.findProduct(3L)).isEmpty();
            assertThat(productCacheStore.findProduct(4L)).isEmpty();
        }

        @DisplayName("저장하면 5분 TTL이 설정된다.")
        @Test
        void setsFiveMinuteTtl_whenProductIsCached() {
            // given
            productCacheStore.putProduct(5L, sampleProductInfo(5L));

            // when
            Long expire = redisTemplate.getExpire(productCacheStore.productKey(5L), TimeUnit.SECONDS);

            // then
            assertThat(expire).isGreaterThan(0).isLessThanOrEqualTo(300);
        }
    }

    @DisplayName("상품 목록 캐시를 다룰 때,")
    @Nested
    class ListCache {

        @DisplayName("저장한 값이 있으면 히트로 조회된다.")
        @Test
        void returnsCachedValue_whenListIsCached() {
            // given
            String key = productCacheStore.listKey(null, "LATEST", 0, 20);
            ProductListCacheValue value = new ProductListCacheValue(List.of(sampleProductInfo(1L)), 1L);
            productCacheStore.putList(key, value);

            // when
            Optional<ProductListCacheValue> result = productCacheStore.findList(key);

            // then
            assertThat(result).contains(value);
        }

        @DisplayName("저장하면 30초 TTL이 설정된다.")
        @Test
        void setsThirtySecondTtl_whenListIsCached() {
            // given
            String key = productCacheStore.listKey(1L, "LATEST", 0, 20);
            productCacheStore.putList(key, new ProductListCacheValue(List.of(), 0L));

            // when
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);

            // then
            assertThat(expire).isGreaterThan(0).isLessThanOrEqualTo(30);
        }
    }
}
