package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대기열 앞에서 일정 주기로 N명을 꺼내 입장 토큰을 발급한다.
 * 배치 크기·주기·TTL은 {@link QueuePolicy} 참고 (순번 조회의 예상 대기 시간 계산과 공유).
 * <p>
 * 대기열 상태를 결정적으로 검증해야 하는 통합 테스트가 백그라운드 발급을 끌 수 있도록
 * 프로퍼티로 게이트한다 (미설정 시 활성 — 운영 동작 변화 없음).
 */
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
@Component
public class EntryTokenScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Scheduled(fixedDelay = QueuePolicy.SCHEDULER_PERIOD_MILLIS)
    public void issueEntryTokens() {
        List<Long> userIds = waitingQueueRepository.popMin(QueuePolicy.BATCH_SIZE);
        if (userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            entryTokenRepository.issue(userId, QueuePolicy.TOKEN_TTL);
        }
        log.info("입장 토큰 발급 완료 — {}명", userIds.size());
    }
}
