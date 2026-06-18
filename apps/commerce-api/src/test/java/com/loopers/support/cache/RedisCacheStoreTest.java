package com.loopers.support.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.loopers.application.product.ProductDetailInfo;

class RedisCacheStoreTest {

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisCacheStore redisCacheStore = new RedisCacheStore(redisTemplate, new ObjectMapper().registerModule(new ParameterNamesModule()));

    @DisplayName("조회 중 Redis 예외가 나면 빈 Optional로 폴백한다.")
    @Test
    void returnsEmpty_whenRedisFailsOnFind() {
        // arrange
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:detail:1")).willThrow(new RuntimeException("Redis down"));

        // act
        Optional<ProductDetailInfo> found = redisCacheStore.find("product:detail:1", ProductDetailInfo.class);

        // assert
        assertThat(found).isEmpty();
    }

    @DisplayName("저장 중 Redis 예외가 나도 요청 흐름을 깨지 않는다.")
    @Test
    void doesNotThrow_whenRedisFailsOnPut() {
        // arrange
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RuntimeException("Redis down"))
            .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // act & assert
        assertThatCode(() -> redisCacheStore.put("product:detail:1",
            new ProductDetailInfo(1L, "a", "b", 1L, "c", 1, true, 0), Duration.ofSeconds(60)))
            .doesNotThrowAnyException();
    }
}
