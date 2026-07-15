package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRedisStore;
import com.loopers.domain.queue.QueueStatusInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기열 Application Service — Redis 대기열 primitive(QueueRedisStore)와
 * HTTP 컨트롤러/스케줄러 사이의 오케스트레이션 계층.
 */
@RequiredArgsConstructor
@Service
public class QueueApplicationService {

    private final QueueRedisStore queueRedisStore;
    private final QueueProperties queueProperties;

    public QueueStatusInfo enter(Long productId, Long userId) {
        queueRedisStore.enter(productId, userId, System.currentTimeMillis());
        // 방금 반영된 순위를 그대로 응답에 반영하기 위해 상태를 다시 조회한다.
        return status(productId, userId);
    }

    public QueueStatusInfo status(Long productId, Long userId) {
        QueueRedisStore.QueueStatus queueStatus = queueRedisStore.status(productId, userId);
        String status = queueStatus.state().name();
        Long pollIntervalMillis = resolvePollInterval(queueStatus.state(), queueStatus.position());
        return new QueueStatusInfo(status, queueStatus.position(), queueStatus.waitingCount(), pollIntervalMillis);
    }

    public void leave(Long productId, Long userId) {
        queueRedisStore.leave(productId, userId);
    }

    public boolean isGated(Long productId) {
        return queueProperties.isGated(productId);
    }

    public QueueRedisStore.TokenLockResult lock(Long productId, Long userId) {
        return queueRedisStore.tryLock(productId, userId);
    }

    public void unlock(Long productId, Long userId) {
        queueRedisStore.unlock(productId, userId);
    }

    public void consume(Long productId, Long userId) {
        queueRedisStore.consumeToken(productId, userId);
    }

    private Long resolvePollInterval(QueueRedisStore.QueueStatus.State state, Long position) {
        if (state != QueueRedisStore.QueueStatus.State.WAITING) {
            return null;
        }
        if (position != null && position <= 10) {
            return queueProperties.pollIntervalNearMillis();
        }
        if (position != null && position <= 100) {
            return queueProperties.pollIntervalMidMillis();
        }
        return queueProperties.pollIntervalFarMillis();
    }
}
