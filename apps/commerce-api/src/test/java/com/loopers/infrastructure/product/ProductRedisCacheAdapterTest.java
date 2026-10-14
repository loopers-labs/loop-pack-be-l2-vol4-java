package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductRedisCacheAdapter 의 단위 테스트.
 * - 키 포맷 / TTL 값이 정확히 지정되는지
 * - SCAN + DEL 패턴이 brandId 별로 동작하는지
 * - Redis 장애 시 예외가 새어나가지 않고 fallback 으로 동작하는지
 */
class ProductRedisCacheAdapterTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductRedisCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new ProductRedisCacheAdapter(redisTemplate, objectMapper);
    }

    @DisplayName("상세 캐시")
    @Nested
    class Detail {

        @DisplayName("putDetail — 키 'product:detail:{id}', TTL 5분으로 저장한다.")
        @Test
        void put_usesCorrectKeyAndTtl() {
            // arrange
            ProductDetailInfo info = sampleDetail(42L);

            // act
            adapter.putDetail(info);

            // assert
            verify(valueOps).set(eq("product:detail:42"), any(String.class), eq(Duration.ofMinutes(5)));
        }

        @DisplayName("getDetail — 캐시 미스 시 Optional.empty 를 반환한다.")
        @Test
        void get_missReturnsEmpty() {
            // arrange
            when(valueOps.get("product:detail:42")).thenReturn(null);

            // act
            Optional<ProductDetailInfo> result = adapter.getDetail(42L);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("getDetail — 직렬화된 JSON 을 역직렬화해 반환한다.")
        @Test
        void get_hitDeserializesJson() throws Exception {
            // arrange
            ProductDetailInfo expected = sampleDetail(42L);
            String json = objectMapper.writeValueAsString(expected);
            when(valueOps.get("product:detail:42")).thenReturn(json);

            // act
            Optional<ProductDetailInfo> result = adapter.getDetail(42L);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().productId()).isEqualTo(42L);
            assertThat(result.get().productName()).isEqualTo("샘플");
        }

        @DisplayName("evictDetail — DEL 'product:detail:{id}' 를 호출한다.")
        @Test
        void evict_deletesByKey() {
            // act
            adapter.evictDetail(42L);

            // assert
            verify(redisTemplate).delete("product:detail:42");
        }
    }

    @DisplayName("목록 캐시")
    @Nested
    class ListCache {

        @DisplayName("putList — brandId 가 있을 때 키 'product:list:{brandId}:{sort}', TTL 1분으로 저장한다.")
        @Test
        void put_withBrandId() {
            // act
            adapter.putList(7L, ProductSortType.LIKES_DESC, List.of(sampleInfo(1L)));

            // assert
            verify(valueOps).set(eq("product:list:7:LIKES_DESC"), any(String.class), eq(Duration.ofMinutes(1)));
        }

        @DisplayName("putList — brandId 가 null 일 때 키 'product:list:all:{sort}' 로 저장한다.")
        @Test
        void put_withoutBrandId_usesAllScope() {
            // act
            adapter.putList(null, ProductSortType.LATEST, List.of(sampleInfo(1L)));

            // assert
            verify(valueOps).set(eq("product:list:all:LATEST"), any(String.class), eq(Duration.ofMinutes(1)));
        }
    }

    @DisplayName("브랜드별 목록 캐시 무효화")
    @Nested
    class EvictByBrand {

        @DisplayName("SCAN 으로 찾은 키들을 DEL 한다.")
        @Test
        void scansAndDeletes() {
            // arrange
            List<String> matchedKeys = List.of("product:list:7:LATEST", "product:list:7:LIKES_DESC");
            Cursor<String> cursor = stubCursor(matchedKeys);
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

            // act
            adapter.evictListsByBrand(7L);

            // assert
            verify(redisTemplate).delete(eq(matchedKeys));
        }

        @DisplayName("SCAN 결과가 비어 있으면 DEL 을 호출하지 않는다.")
        @Test
        void noKeys_skipsDelete() {
            // arrange
            Cursor<String> emptyCursor = stubCursor(List.of());
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(emptyCursor);

            // act
            adapter.evictListsByBrand(7L);

            // assert
            verify(redisTemplate, never()).delete(anyList());
        }
    }

    @DisplayName("Redis 장애 격리")
    @Nested
    class FailureIsolation {

        @DisplayName("get 호출 중 RedisConnectionFailureException 이 나도, Optional.empty 로 fallback 한다.")
        @Test
        void get_swallowsException() {
            // arrange
            when(valueOps.get(any(String.class))).thenThrow(new RedisConnectionFailureException("down"));

            // act
            Optional<ProductDetailInfo> result = adapter.getDetail(42L);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("put 호출 중 RedisConnectionFailureException 이 나도, 호출자에게 전파되지 않는다.")
        @Test
        void put_swallowsException() {
            // arrange
            doThrowOnSet(new RedisConnectionFailureException("down"));

            // act & assert — 예외 없이 통과
            adapter.putDetail(sampleDetail(42L));
            verify(valueOps, times(1)).set(any(String.class), any(String.class), any(Duration.class));
        }
    }

    private void doThrowOnSet(RuntimeException ex) {
        org.mockito.Mockito.doThrow(ex).when(valueOps)
            .set(any(String.class), any(String.class), any(Duration.class));
    }

    private static ProductDetailInfo sampleDetail(long id) {
        return new ProductDetailInfo(
            id, "샘플", "설명", 1000L, 10, 0L, null,
            ProductStatus.ACTIVE, 1L, "나이키", "스포츠"
        );
    }

    private static ProductInfo sampleInfo(long id) {
        return new ProductInfo(id, 1L, "이름", "설명", 1000L, 10, 0L, null, ProductStatus.ACTIVE);
    }

    @SuppressWarnings("unchecked")
    private static Cursor<String> stubCursor(List<String> values) {
        Iterator<String> iter = values.iterator();
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenAnswer(inv -> iter.hasNext());
        when(cursor.next()).thenAnswer(inv -> {
            if (!iter.hasNext()) throw new NoSuchElementException();
            return iter.next();
        });
        // Iterator.forEachRemaining 의 default 구현이 Mockito mock 에선 override 되어 no-op 이 되므로 명시 stub.
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<String> consumer = inv.getArgument(0);
            while (iter.hasNext()) consumer.accept(iter.next());
            return null;
        }).when(cursor).forEachRemaining(org.mockito.ArgumentMatchers.any());
        return cursor;
    }
}
