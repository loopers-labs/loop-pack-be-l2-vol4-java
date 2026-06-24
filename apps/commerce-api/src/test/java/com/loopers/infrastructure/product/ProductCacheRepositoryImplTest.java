package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheRepositoryImplTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @DisplayName("상품 상세 캐시 직렬화에 실패해도 조회 흐름에 예외를 전파하지 않는다.")
    @Test
    void ignoresSerializationFailure_whenCachingProductDetail() throws Exception {
        // arrange
        ProductCacheRepositoryImpl productCacheRepository = new ProductCacheRepositoryImpl(redisTemplate, objectMapper);
        ProductInfo productInfo = productInfo();
        when(objectMapper.writeValueAsString(productInfo)).thenThrow(jsonProcessingException());

        // act & assert
        assertThatCode(() -> productCacheRepository.cacheProduct(productInfo))
            .doesNotThrowAnyException();
        verify(redisTemplate, never()).opsForValue();
    }

    @DisplayName("Redis 조회가 실패하면 캐시 miss로 처리한다.")
    @Test
    void returnsEmpty_whenRedisGetFails() {
        // arrange
        ProductCacheRepositoryImpl productCacheRepository = new ProductCacheRepositoryImpl(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:detail:1")).thenThrow(new RedisConnectionFailureException("redis down"));

        // act & assert
        assertThat(productCacheRepository.getProduct(1L)).isEmpty();
    }

    @DisplayName("상품 목록 캐시를 저장하면 목록 키 추적 set에도 TTL을 설정한다.")
    @Test
    void setsTtlOnListKeyRegistry_whenCachingProductList() throws Exception {
        // arrange
        ProductCacheRepositoryImpl productCacheRepository = new ProductCacheRepositoryImpl(redisTemplate, objectMapper);
        List<ProductInfo> productInfos = List.of(productInfo());
        String cacheKey = "product:list:brand:1:sort:likes_desc:page:0:size:20";
        when(objectMapper.writeValueAsString(productInfos)).thenReturn("[]");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        // act
        productCacheRepository.cacheProducts(1L, "likes_desc", 0, 20, productInfos);

        // assert
        verify(valueOperations).set(cacheKey, "[]", Duration.ofSeconds(30));
        verify(setOperations).add("product:list:keys", cacheKey);
        verify(redisTemplate).expire("product:list:keys", Duration.ofSeconds(60));
    }

    @DisplayName("상품 목록 캐시 직렬화에 실패하면 Redis 명령을 실행하지 않는다.")
    @Test
    void skipsRedisCommand_whenCachingProductListSerializationFails() throws Exception {
        // arrange
        ProductCacheRepositoryImpl productCacheRepository = new ProductCacheRepositoryImpl(redisTemplate, objectMapper);
        List<ProductInfo> productInfos = List.of(productInfo());
        when(objectMapper.writeValueAsString(productInfos)).thenThrow(jsonProcessingException());

        // act & assert
        assertThatCode(() -> productCacheRepository.cacheProducts(1L, "likes_desc", 0, 20, productInfos))
            .doesNotThrowAnyException();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForSet();
    }

    private ProductInfo productInfo() {
        return new ProductInfo(
            1L,
            new ProductInfo.BrandInfo(1L, "Loopers", "감성 이커머스 브랜드"),
            "니트",
            "부드러운 니트",
            30_000L,
            10,
            5
        );
    }

    private JsonProcessingException jsonProcessingException() {
        return new JsonProcessingException("직렬화 실패") {};
    }
}
