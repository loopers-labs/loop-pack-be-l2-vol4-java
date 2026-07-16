package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.config.QueueProperties;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 발급 로직의 결정적 검증 — 백그라운드 @Scheduled 개입 없이 tick 을 직접 호출한다
 * (test 프로필은 queue.enabled=false 라 스케줄러 빈이 등록되지 않으므로 수동 조립).
 * @Scheduled 배선 자체는 {@code EntryTokenSchedulingWiringTest} 에서 검증한다.
 */
@SpringBootTest
class EntryTokenSchedulerIntegrationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private EntryTokenScheduler scheduler(int batchSize) {
        QueueProperties properties = new QueueProperties(
                true, 300,
                new QueueProperties.Scheduler(100, batchSize),
                new QueueProperties.Polling(List.of(), 5000)
        );
        return new EntryTokenScheduler(waitingQueueRepository, entryTokenRepository, properties);
    }

    @Test
    @DisplayName("한 tick 은 앞에서부터 batch-size 명에게만 토큰을 발급한다 — 초과 유입은 다음 tick 으로 넘어간다.")
    void issuesAtMostBatchSizePerTick() {
        for (long userId = 1; userId <= 5; userId++) {
            waitingQueueRepository.enter(userId, userId * 100);
        }
        EntryTokenScheduler scheduler = scheduler(2);

        scheduler.issueTokens();

        // 앞 2명만 발급, 나머지 3명은 대기 유지
        assertThat(entryTokenRepository.find(1L)).isPresent();
        assertThat(entryTokenRepository.find(2L)).isPresent();
        assertThat(entryTokenRepository.find(3L)).isEmpty();
        assertThat(waitingQueueRepository.size()).isEqualTo(3);

        scheduler.issueTokens();

        assertThat(entryTokenRepository.find(3L)).isPresent();
        assertThat(entryTokenRepository.find(4L)).isPresent();
        assertThat(entryTokenRepository.find(5L)).isEmpty();
        assertThat(waitingQueueRepository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 대기열에서는 아무것도 발급하지 않는다.")
    void issuesNothingWhenQueueIsEmpty() {
        EntryTokenScheduler scheduler = scheduler(10);

        scheduler.issueTokens();

        assertThat(waitingQueueRepository.size()).isZero();
    }

    @Test
    @DisplayName("발급된 토큰은 유저마다 서로 다른 값이다.")
    void issuesDistinctTokens() {
        waitingQueueRepository.enter(1L, 100);
        waitingQueueRepository.enter(2L, 200);

        scheduler(2).issueTokens();

        String token1 = entryTokenRepository.find(1L).orElseThrow();
        String token2 = entryTokenRepository.find(2L).orElseThrow();
        assertThat(token1).isNotEqualTo(token2);
    }
}
