package com.loopers.infrastructure.waitingqueue;

import com.loopers.domain.waitingqueue.EntryTokenRepository;
import com.loopers.domain.waitingqueue.WaitingQueueService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 안전캡(선택 B) 검증 — 실제 Redis(Testcontainers).
 *
 * <p>방류형은 활성 상한 gate가 없으나, {@code hard-max-active}를 켜면 활성이 그 상한에 도달할 때
 * 방류를 조이는 <b>비상 브레이크</b>가 동작한다. 윈도우 레이트리밋과 독립적으로 작동하는지 보기 위해
 * 방류 주기(M)를 크게(3600s) 두어 윈도우 예산(=N=30)이 리미터가 되지 않게 하고, hardMax=5로 낮춘다.
 * 이 조건에선 방류량이 오직 {@code hardMax − 현재활성}으로 결정된다.
 */
@SpringBootTest(properties = {
    "waiting-queue.enabled=true",
    "waiting-queue.release-size=30",
    "waiting-queue.scheduler-interval-seconds=3600", // 윈도우를 크게 → 윈도우 예산이 리미터가 안 되게
    "waiting-queue.hard-max-active=5"
})
class WaitingQueueHardCapRedisIntegrationTest {

    @Autowired WaitingQueueService service;
    @Autowired EntryTokenRepository tokens;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("hardMax 안전망: 활성이 상한(5)에 도달하면 방류를 조이고, 슬롯 회수 시 그만큼만 재방류한다")
    @Test
    void hardCapThrottlesRelease() {
        // 100명 대기 — 방류 N=30, 윈도우 예산 30, 그러나 hardMax=5가 더 강한 제약
        for (long u = 1; u <= 100; u++) {
            service.enter(u);
        }

        // 1차: min(윈도우예산 30, hardMax 5 − 활성 0) = 5명만 방류
        int first = service.issueBatch();
        assertThat(first).isEqualTo(5);
        assertThat(tokens.activeCountLive()).isEqualTo(5L);
        assertThat(service.status().queueSize()).isEqualTo(95L);

        // 2차: 활성이 이미 상한(5) → headroom 0 → 방류 0 (윈도우 예산은 남아 있어도 안전망이 막음)
        int second = service.issueBatch();
        assertThat(second).isEqualTo(0);
        assertThat(tokens.activeCountLive()).isEqualTo(5L);

        // 슬롯 1개 회수(주문 성공 소모) → 활성 4
        String token = tokens.findTokenByUser(1L);
        service.consume(1L, token);
        assertThat(tokens.activeCountLive()).isEqualTo(4L);

        // 3차: headroom 1 → 정확히 1명만 재방류(활성 다시 상한 5로 복귀)
        int third = service.issueBatch();
        assertThat(third).isEqualTo(1);
        assertThat(tokens.activeCountLive()).isEqualTo(5L);
        assertThat(service.status().queueSize()).isEqualTo(94L); // 5 + 1 방류, 100 − 6
    }
}
