package com.loopers.infrastructure.cache;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.domain.productrank.RankedProduct;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheStoreTest {

    @Autowired ProductCacheStore store;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("put 후 get 하면 동일 값이 복원된다")
    @Test
    void put_then_get_round_trip() {
        ProductDetailInfo info = new ProductDetailInfo(1L, 7L, "brand", "n", "d", 1000L, 10, 5L);

        store.putDetail(info, Duration.ofMinutes(10));

        assertThat(store.getDetail(1L)).contains(info);
    }

    @DisplayName("미스면 빈 Optional 을 반환한다(DB 폴백)")
    @Test
    void miss_returns_empty() {
        assertThat(store.getDetail(999L)).isEmpty();
    }

    @DisplayName("evict 후에는 미스가 된다")
    @Test
    void evict_removes() {
        ProductDetailInfo info = new ProductDetailInfo(2L, 7L, "b", "n", "d", 1L, 1, 0L);
        store.putDetail(info, Duration.ofMinutes(10));

        store.evictDetail(2L);

        assertThat(store.getDetail(2L)).isEmpty();
    }

    @DisplayName("목록 블롭 put/get 라운드트립")
    @Test
    void likes_blob_round_trip() {
        List<RankedProduct> blob = List.of(new RankedProduct(1L, 50L), new RankedProduct(2L, 30L));

        store.putLikesBlob(7L, blob, Duration.ofSeconds(60));

        assertThat(store.getLikesBlob(7L)).contains(blob);
    }

    @DisplayName("목록 블롭 미스면 빈 Optional(DB 폴백)")
    @Test
    void likes_blob_miss_empty() {
        assertThat(store.getLikesBlob(999L)).isEmpty();
    }
}
