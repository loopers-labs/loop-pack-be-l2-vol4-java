package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenDlqRepository;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완료 기준 검증: 실제 Redis + 스케줄러 end-to-end.
 * 테스트 프로파일에서 스케줄러는 비활성(@ConditionalOnProperty)이므로 tick()을 직접 호출한다.
 */
@SpringBootTest
class QueueSchedulerIntegrationTest {

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private EntryTokenDlqRepository entryTokenDlqRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("tick 1회 → 대기열 앞 N명이 큐에서 빠지고 각자 유효한 입장 토큰을 보유한다.")
    @Test
    void tickAdmitsFrontUsers_withRealRedis() {
        // arrange: 배치 크기 2로 스케줄러 구성, 3명 진입
        QueueScheduler scheduler = new QueueScheduler(
            waitingQueueService, entryTokenService, entryTokenDlqRepository, 2
        );
        waitingQueueService.enter(1L);
        waitingQueueService.enter(2L);
        waitingQueueService.enter(3L);

        // act
        scheduler.tick();

        // assert: 앞 2명은 큐에서 빠지고 토큰 보유, 3번은 아직 대기(순번 0)
        assertThat(entryTokenRepository.find(1L)).isPresent();
        assertThat(entryTokenRepository.find(2L)).isPresent();
        assertThat(entryTokenService.validate(1L, entryTokenRepository.find(1L).orElseThrow())).isTrue();
        assertThat(waitingQueueService.getPosition(3L).rank()).isZero();
        assertThat(waitingQueueService.getPosition(3L).total()).isEqualTo(1L);
    }
}
