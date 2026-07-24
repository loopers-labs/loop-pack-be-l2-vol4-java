package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenDlqRepository;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 스케줄러. OutboxRelay 패턴을 본떴다.
 * <p>
 * 주기적으로 대기열 앞 N명을 꺼내(ZPOPMIN) 입장 토큰을 발급한다.
 * <ul>
 *   <li>배치 크기 N: DB 커넥션 풀 40 / 주문 200ms(가정) = 200 TPS → 안전마진 70% = 140 TPS → 100ms tick당 14명.</li>
 *   <li>토큰 발급 실패(유실 위험)는 메인 큐가 아니라 DLQ에 담아 재시도한다(순번 역전 방지).</li>
 * </ul>
 * {@code queue.scheduler.enabled=false}면 비활성화된다(타이밍 의존 없는 단위 테스트용, tick 직접 호출).
 */
@Component
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class QueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueScheduler.class);

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final EntryTokenDlqRepository entryTokenDlqRepository;
    private final int batchSize;

    @Autowired
    public QueueScheduler(
        WaitingQueueService waitingQueueService,
        EntryTokenService entryTokenService,
        EntryTokenDlqRepository entryTokenDlqRepository,
        @Value("${queue.scheduler.batch-size:14}") int batchSize
    ) {
        this.waitingQueueService = waitingQueueService;
        this.entryTokenService = entryTokenService;
        this.entryTokenDlqRepository = entryTokenDlqRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler.interval-ms:100}")
    public void tick() {
        // 이미 자리를 딴 실패분(DLQ)을 먼저 청산한 뒤, 신규 입장을 받는다(공정성).
        retryFailed();
        admit();
    }

    /** 대기열 앞 N명을 꺼내 토큰을 발급한다. */
    void admit() {
        for (Long userId : waitingQueueService.pollFront(batchSize)) {
            issueOrDeadLetter(userId);
        }
    }

    /** DLQ에 쌓인 실패분을 최대 N개 재시도한다. */
    void retryFailed() {
        for (Long userId : entryTokenDlqRepository.drain(batchSize)) {
            issueOrDeadLetter(userId);
        }
    }

    /**
     * 토큰 발급을 시도하고, 실패하면 메인 큐가 아니라 DLQ로 보낸다.
     * ZPOPMIN으로 이미 큐에서 빠진 유저라 메인 큐 재삽입은 순번 역전을 부른다.
     */
    private void issueOrDeadLetter(Long userId) {
        try {
            entryTokenService.issue(userId);
        } catch (Exception e) {
            log.warn("입장 토큰 발급 실패, DLQ로 이동: userId={}", userId, e);
            entryTokenDlqRepository.push(userId);
        }
    }
}
