package com.loopers.infrastructure.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLikeCountStoreTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLikeCountStore store;

    @BeforeEach
    void setUp() {
        store = new RedisLikeCountStore(redisTemplate);
    }

    @DisplayName("좋아요 증가 시 해당 상품 증감분 키를 +1 INCRBY 한다")
    @Test
    void increment_incrementsDeltaByOne() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        store.increment(7L);

        // then
        verify(valueOperations).increment(RedisLikeCountStore.DELTA_KEY_PREFIX + 7L, 1L);
    }

    @DisplayName("좋아요 취소 시 해당 상품 증감분 키를 -1 INCRBY 한다")
    @Test
    void decrement_incrementsDeltaByMinusOne() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        store.decrement(7L);

        // then
        verify(valueOperations).increment(RedisLikeCountStore.DELTA_KEY_PREFIX + 7L, -1L);
    }

    @DisplayName("Redis 장애로 증감분 반영이 실패해도 예외를 전파하지 않는다(좋아요 관계는 이미 커밋됨)")
    @Test
    void swallowsException_whenRedisFails() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), anyLong())).thenThrow(new RuntimeException("redis down"));

        // when / then
        assertThatCode(() -> store.increment(7L)).doesNotThrowAnyException();
    }
}
