package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheStoreTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisCacheStore cacheStore;

    private static final Duration TTL = Duration.ofMinutes(5);

    record Sample(Long id, String name) {
    }

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        cacheStore = new RedisCacheStore(redisTemplate, objectMapper);
    }

    @Nested
    @DisplayName("getOrLoad 시")
    class GetOrLoad {

        @DisplayName("캐시에 값이 있으면 loader를 호출하지 않고 역직렬화 결과를 반환한다")
        @Test
        void returnsCached_withoutLoader() throws Exception {
            // given
            Sample cached = new Sample(1L, "cached");
            when(valueOperations.get("k")).thenReturn(objectMapper.writeValueAsString(cached));
            AtomicInteger loaderCalls = new AtomicInteger();

            // when
            Sample result = cacheStore.getOrLoad("k", Sample.class, TTL, () -> {
                loaderCalls.incrementAndGet();
                return new Sample(2L, "loaded");
            });

            // then
            assertThat(result).isEqualTo(cached);
            assertThat(loaderCalls.get()).isZero();
        }

        @DisplayName("캐시 미스면 loader 결과를 반환하고 TTL과 함께 저장한다")
        @Test
        void loadsAndStores_onMiss() {
            // given
            when(valueOperations.get("k")).thenReturn(null);

            // when
            Sample result = cacheStore.getOrLoad("k", Sample.class, TTL, () -> new Sample(2L, "loaded"));

            // then
            assertThat(result).isEqualTo(new Sample(2L, "loaded"));
            verify(valueOperations).set(eq("k"), anyString(), eq(TTL));
        }

        @DisplayName("Redis 조회가 실패해도 loader로 폴백해 정상 반환한다")
        @Test
        void fallsBackToLoader_whenRedisFails() {
            // given
            when(valueOperations.get("k")).thenThrow(new RuntimeException("redis down"));

            // when
            Sample result = cacheStore.getOrLoad("k", Sample.class, TTL, () -> new Sample(2L, "loaded"));

            // then
            assertThat(result).isEqualTo(new Sample(2L, "loaded"));
        }
    }
}
