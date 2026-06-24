package com.loopers.infrastructure.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeCountRepositoryImplTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @DisplayName("Redis 카운터 증가 중 장애가 발생하면 실패를 반환한다.")
    @Test
    void returnsFalse_whenRedisFailsDuringIncrease() {
        // arrange
        ProductLikeCountRepositoryImpl repository = new ProductLikeCountRepositoryImpl(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("product:like-count:1", "3"))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        // act
        boolean result = repository.increase(1L, 3);

        // assert
        assertThat(result).isFalse();
    }

    @DisplayName("Redis 카운터 조회 중 장애가 발생하면 빈 값을 반환한다.")
    @Test
    void returnsEmpty_whenRedisFailsDuringGet() {
        // arrange
        ProductLikeCountRepositoryImpl repository = new ProductLikeCountRepositoryImpl(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:like-count:1"))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        // act
        var result = repository.get(1L);

        // assert
        assertThat(result).isEmpty();
    }
}
