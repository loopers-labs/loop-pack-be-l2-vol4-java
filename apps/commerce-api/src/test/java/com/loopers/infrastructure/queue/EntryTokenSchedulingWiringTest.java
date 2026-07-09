package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * queue.enabled=true 일 때 @Scheduled 배선이 실제로 도는지 검증한다
 * (조건부 빈 등록 + fixedDelayString 프로퍼티 바인딩). 발급 로직의 결정적 검증은
 * {@code EntryTokenSchedulerIntegrationTest} 가 담당한다.
 *
 * <p>@DirtiesContext: 스케줄러가 컨텍스트 캐시에 살아남아 다른 테스트의 대기열을
 * 백그라운드로 pop 하지 않도록 클래스 종료 시 컨텍스트를 닫는다.</p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "queue.enabled=true",
        "queue.scheduler.interval-ms=100",
        "queue.scheduler.batch-size=5",
})
class EntryTokenSchedulingWiringTest {

    private static final long TIMEOUT_MILLIS = 5_000;

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

    @Test
    @DisplayName("대기열에 유저를 세우면 스케줄러가 주기적으로 꺼내 토큰을 발급한다.")
    void backgroundSchedulerIssuesTokens() throws InterruptedException {
        waitingQueueRepository.enter(1L, System.currentTimeMillis());

        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (entryTokenRepository.find(1L).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(entryTokenRepository.find(1L)).isPresent();
        assertThat(waitingQueueRepository.size()).isZero();
    }
}
