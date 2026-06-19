package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.product.ProductSortType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ProductCacheService productCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        productCacheService = new ProductCacheService(redisTemplate, objectMapper);
    }

    private static ProductDetailInfo sampleDetail(Long id) {
        return new ProductDetailInfo(id, 1L, "브랜드", "상품", "설명", 10000L, 5, "img.jpg", 7L, ZonedDateTime.now());
    }

    @DisplayName("상품 상세 캐시를 조회할 때,")
    @Nested
    class GetProductDetail {

        @DisplayName("캐시에 데이터가 있으면 반환한다.")
        @Test
        void returns_cached_data_when_present() throws Exception {
            ProductDetailInfo detail = sampleDetail(1L);
            String json = objectMapper.writeValueAsString(detail);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("product:detail:1")).thenReturn(json);

            Optional<ProductDetailInfo> result = productCacheService.getProductDetail(1L);

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
        }

        @DisplayName("캐시에 데이터가 없으면 빈 Optional 을 반환한다.")
        @Test
        void returns_empty_when_cache_miss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("product:detail:1")).thenReturn(null);

            Optional<ProductDetailInfo> result = productCacheService.getProductDetail(1L);

            assertThat(result).isEmpty();
        }

        @DisplayName("Redis 장애 시 빈 Optional 을 반환한다.")
        @Test
        void returns_empty_on_redis_failure() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            Optional<ProductDetailInfo> result = productCacheService.getProductDetail(1L);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("상품 상세를 캐시에 저장할 때,")
    @Nested
    class CacheProductDetail {

        @DisplayName("JSON 으로 직렬화하여 TTL 과 함께 저장한다.")
        @Test
        void stores_with_ttl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            productCacheService.cacheProductDetail(1L, sampleDetail(1L));

            verify(valueOperations).set(eq("product:detail:1"), anyString(), eq(10L), eq(TimeUnit.MINUTES));
        }

        @DisplayName("Redis 장애 시 예외를 던지지 않는다.")
        @Test
        void does_not_throw_on_redis_failure() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            productCacheService.cacheProductDetail(1L, sampleDetail(1L));
        }
    }

    @DisplayName("캐시를 무효화할 때,")
    @Nested
    class Evict {

        @DisplayName("상품 상세 캐시를 삭제한다.")
        @Test
        void evicts_product_detail() {
            productCacheService.evictProductDetail(1L);

            verify(redisTemplate).delete("product:detail:1");
        }

        @DisplayName("상품 목록 캐시를 패턴으로 삭제한다.")
        @Test
        void evicts_product_list_by_pattern() {
            when(redisTemplate.keys("product:list:*")).thenReturn(Set.of("product:list:brand:1:sort:LATEST"));

            productCacheService.evictAllProductLists();

            verify(redisTemplate).delete(Set.of("product:list:brand:1:sort:LATEST"));
        }
    }
}
