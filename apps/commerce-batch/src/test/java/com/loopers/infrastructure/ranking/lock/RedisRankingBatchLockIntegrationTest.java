package com.loopers.infrastructure.ranking.lock;

import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class RedisRankingBatchLockIntegrationTest {

    @Autowired private RedisRankingBatchLock lock;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("tryLock()을 호출할 때,")
    @Nested
    class TryLock {

        @DisplayName("아무도 잡고 있지 않은 키면 락 획득에 성공한다.")
        @Test
        void succeeds_whenKeyIsNotLocked() {
            boolean acquired = lock.tryLock("ranking:batch:lock:WEEKLY:2026W30", UUID.randomUUID().toString(), Duration.ofMinutes(1));

            assertThat(acquired).isTrue();
        }

        @DisplayName("이미 다른 token이 잡고 있는 키면 락 획득에 실패한다.")
        @Test
        void fails_whenKeyIsAlreadyLockedByAnotherToken() {
            String key = "ranking:batch:lock:WEEKLY:2026W30";
            lock.tryLock(key, UUID.randomUUID().toString(), Duration.ofMinutes(1));

            boolean acquired = lock.tryLock(key, UUID.randomUUID().toString(), Duration.ofMinutes(1));

            assertThat(acquired).isFalse();
        }
    }

    @DisplayName("unlock()을 호출할 때,")
    @Nested
    class Unlock {

        @DisplayName("자신이 획득한 token으로 해제하면 이후 같은 키를 다시 잡을 수 있다.")
        @Test
        void allowsRelock_whenUnlockedWithOwnToken() {
            String key = "ranking:batch:lock:WEEKLY:2026W30";
            String token = UUID.randomUUID().toString();
            lock.tryLock(key, token, Duration.ofMinutes(1));

            lock.unlock(key, token);

            assertThat(lock.tryLock(key, UUID.randomUUID().toString(), Duration.ofMinutes(1))).isTrue();
        }

        @DisplayName("다른 token으로 해제를 시도하면 무시되어 원래 소유자의 락이 유지된다.")
        @Test
        void keepsLock_whenUnlockedWithWrongToken() {
            String key = "ranking:batch:lock:WEEKLY:2026W30";
            String ownerToken = UUID.randomUUID().toString();
            lock.tryLock(key, ownerToken, Duration.ofMinutes(1));

            lock.unlock(key, UUID.randomUUID().toString());

            assertThat(lock.tryLock(key, UUID.randomUUID().toString(), Duration.ofMinutes(1))).isFalse();
        }
    }
}