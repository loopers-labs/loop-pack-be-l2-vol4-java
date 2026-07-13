package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.AdmissionLock;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisAdmissionLockIntegrationTest {

    @Autowired
    private AdmissionLock sut;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("비어 있으면 락을 획득한다")
    void givenFreeLock_whenTryAcquire_thenTrue() {
        assertThat(sut.tryAcquire(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("이미 잡혀 있으면 락 획득에 실패한다")
    void givenHeldLock_whenTryAcquire_thenFalse() {
        sut.tryAcquire(Duration.ofSeconds(5));

        assertThat(sut.tryAcquire(Duration.ofSeconds(5))).isFalse();
    }
}
