package com.loopers.support.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.application.product.CachedProductSummaryInfos;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RedisCacheStoreIntegrationTest {

    @Autowired
    private RedisCacheStore redisCacheStore;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("저장한 값을 같은 키로 다시 조회하면 동일한 값을 반환한다.")
    @Test
    void returnsStoredValue_whenFoundByKey() {
        // arrange
        String key = "product:detail:1";
        ProductDetailInfo stored = new ProductDetailInfo(1L, "감성 가디건", "포근한 가디건", 1L, "감성 브랜드", 39_000, true, 7);
        redisCacheStore.put(key, stored, Duration.ofSeconds(60));

        // act
        Optional<ProductDetailInfo> found = redisCacheStore.find(key, ProductDetailInfo.class);

        // assert
        assertThat(found).contains(stored);
    }

    @DisplayName("저장한 적 없는 키를 조회하면 빈 Optional을 반환한다.")
    @Test
    void returnsEmpty_whenKeyIsAbsent() {
        // act
        Optional<ProductDetailInfo> found = redisCacheStore.find("product:detail:999", ProductDetailInfo.class);

        // assert
        assertThat(found).isEmpty();
    }

    @DisplayName("트랜잭션 안에서 evictAfterCommit을 호출하면 커밋 이후에 키가 삭제된다.")
    @Test
    void evictsAfterCommit_whenInsideTransaction() {
        // arrange
        String key = "product:detail:1";
        redisCacheStore.put(key, new ProductDetailInfo(
            1L, "감성 가디건", "포근한 가디건", 1L, "감성 브랜드", 39_000, true, 7), Duration.ofSeconds(60));

        // act
        boolean[] presentDuringTx = {false};
        transactionTemplate.executeWithoutResult(status -> {
            redisCacheStore.evictAfterCommit(key);
            presentDuringTx[0] = Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        });

        // assert
        assertAll(
            () -> assertThat(presentDuringTx[0]).isTrue(),
            () -> assertThat(stringRedisTemplate.hasKey(key)).isFalse()
        );
    }

    @DisplayName("트랜잭션이 없으면 evictAfterCommit은 즉시 키를 삭제한다.")
    @Test
    void evictsImmediately_whenNoTransaction() {
        // arrange
        String key = "product:detail:2";
        redisCacheStore.put(key, new ProductDetailInfo(
            2L, "린넨 셔츠", "여름 린넨", 1L, "감성 브랜드", 25_000, true, 3), Duration.ofSeconds(60));

        // act
        redisCacheStore.evictAfterCommit(key);

        // assert
        assertThat(stringRedisTemplate.hasKey(key)).isFalse();
    }

    @DisplayName("getOrLoad: 키가 없으면 loader를 실행해 값을 반환하고 캐시에 적재한다.")
    @Test
    void loadsAndCaches_whenCacheMiss() {
        // arrange
        String key = "product:detail:10";

        // act
        ProductDetailInfo result = redisCacheStore.getOrLoad(key, ProductDetailInfo.class, Duration.ofSeconds(60),
            () -> new ProductDetailInfo(10L, "테스트 상품", "설명", 1L, "브랜드", 10_000, true, 5));

        // assert
        assertAll(
            () -> assertThat(result.productId()).isEqualTo(10L),
            () -> assertThat(redisCacheStore.find(key, ProductDetailInfo.class)).isPresent()
        );
    }

    @DisplayName("getOrLoad: 키가 있으면 loader를 실행하지 않고 캐시 값을 반환한다.")
    @Test
    void returnsCached_whenCacheHit() {
        // arrange
        String key = "product:detail:11";
        ProductDetailInfo stored = new ProductDetailInfo(11L, "캐시 상품", "설명", 1L, "브랜드", 20_000, true, 3);
        redisCacheStore.put(key, stored, Duration.ofSeconds(60));

        // act
        ProductDetailInfo result = redisCacheStore.getOrLoad(key, ProductDetailInfo.class, Duration.ofSeconds(60),
            () -> { throw new IllegalStateException("loader 호출 금지"); });

        // assert
        assertThat(result).isEqualTo(stored);
    }

    @DisplayName("getOrLoad: loader가 예외를 던지면 예외가 전파되고 키가 캐시에 없다.")
    @Test
    void propagatesException_andDoesNotCache_whenLoaderThrows() {
        // arrange
        String key = "product:detail:12";

        // act & assert
        assertThatThrownBy(() -> redisCacheStore.getOrLoad(key, ProductDetailInfo.class, Duration.ofSeconds(60),
            () -> { throw new RuntimeException("loader 실패"); }))
            .isInstanceOf(RuntimeException.class);
        assertThat(redisCacheStore.find(key, ProductDetailInfo.class)).isEmpty();
    }

    @DisplayName("페이지 캐시 DTO(CachedProductSummaryInfos)도 저장·조회 라운드트립이 보존된다.")
    @Test
    void returnsStoredPage_whenFoundByKey() {
        // arrange
        String key = "product:list:likes_desc:p0:s20";
        CachedProductSummaryInfos stored = new CachedProductSummaryInfos(
            List.of(
                new ProductSummaryInfo(1L, "감성 가디건", 1L, "감성 브랜드", 39_000, true, 100),
                new ProductSummaryInfo(2L, "린넨 셔츠", 1L, "감성 브랜드", 25_000, false, 80)
            ), 100_000L);
        redisCacheStore.put(key, stored, Duration.ofSeconds(30));

        // act
        Optional<CachedProductSummaryInfos> found = redisCacheStore.find(key, CachedProductSummaryInfos.class);

        // assert
        assertAll(
            () -> assertThat(found).isPresent(),
            () -> assertThat(found.get().content()).hasSize(2),
            () -> assertThat(found.get().totalElements()).isEqualTo(100_000L)
        );
    }
}
