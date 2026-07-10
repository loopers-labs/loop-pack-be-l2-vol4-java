package com.loopers.infrastructure.ratelimit;

import com.loopers.domain.ratelimit.RateLimitRedisStore;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RateLimitRedisStoreImplTest {

    @Autowired private RateLimitRedisStore rateLimitRedisStore;
    @Autowired private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("한도 이내로 연속 호출하면 매번 true 를 반환한다")
    @Test
    void returnsTrue_everyTime_whenCalledWithinLimit() {
        // Arrange
        String key = newKey();
        int limit = 5;
        int windowSeconds = 60;

        // Act & Assert
        for (int i = 0; i < limit; i++) {
            assertThat(rateLimitRedisStore.tryAcquire(key, limit, windowSeconds)).isTrue();
        }
    }

    @DisplayName("같은 윈도우 안에서 한도를 초과한 직후 호출은 false 를 반환한다")
    @Test
    void returnsFalse_whenCallExceedsLimit_withinSameWindow() {
        // Arrange
        String key = newKey();
        int limit = 5;
        int windowSeconds = 60;
        for (int i = 0; i < limit; i++) {
            assertThat(rateLimitRedisStore.tryAcquire(key, limit, windowSeconds)).isTrue();
        }

        // Act
        boolean result = rateLimitRedisStore.tryAcquire(key, limit, windowSeconds);

        // Assert
        assertThat(result).isFalse();
    }

    @DisplayName("윈도우가 지나 리셋되면 다시 true 를 반환한다")
    @Test
    void returnsTrue_afterWindowResets() throws InterruptedException {
        // Arrange
        String key = newKey();
        int limit = 1;
        int windowSeconds = 1;
        assertThat(rateLimitRedisStore.tryAcquire(key, limit, windowSeconds)).isTrue();
        assertThat(rateLimitRedisStore.tryAcquire(key, limit, windowSeconds)).isFalse();

        // Act
        Thread.sleep(1500);
        boolean result = rateLimitRedisStore.tryAcquire(key, limit, windowSeconds);

        // Assert
        assertThat(result).isTrue();
    }

    @DisplayName("서로 다른 키는 독립적으로 카운트되어, 한 키의 한도 소진이 다른 키에 영향을 주지 않는다")
    @Test
    void doesNotAffectOtherKey_whenOneKeyExhaustsLimit() {
        // Arrange
        String keyA = newKey();
        String keyB = newKey();
        int limit = 3;
        int windowSeconds = 60;
        for (int i = 0; i < limit; i++) {
            assertThat(rateLimitRedisStore.tryAcquire(keyA, limit, windowSeconds)).isTrue();
        }
        assertThat(rateLimitRedisStore.tryAcquire(keyA, limit, windowSeconds)).isFalse();

        // Act
        boolean result = rateLimitRedisStore.tryAcquire(keyB, limit, windowSeconds);

        // Assert
        assertThat(result).isTrue();
    }

    private String newKey() {
        return "ratelimit:test:" + UUID.randomUUID();
    }
}
