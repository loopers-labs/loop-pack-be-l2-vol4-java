package com.loopers.domain.eventpublish;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Outbox 도메인 서비스 — 저장/조회/상태 전이의 트랜잭션 경계를 관리한다.
 * <p>
 * 저장(append)은 도메인 트랜잭션 안에서 호출되어 원자성을 보장한다 (기본 propagation = REQUIRED).
 * 발송 완료 처리(markSent/markFailed)는 짧고 독립적인 트랜잭션(REQUIRES_NEW)으로 분리 — Relay 배치에서
 * 개별 메시지의 처리 결과가 서로에게 영향을 주지 않도록 한다.
 */
@RequiredArgsConstructor
@Component
public class OutboxService {

    private final OutboxRepository outboxRepository;

    @Transactional
    public OutboxMessage append(OutboxMessage message) {
        return outboxRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> loadPendingBatch(int limit) {
        return outboxRepository.findPendingBatch(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        outboxRepository.findById(id).ifPresent(m -> {
            m.markSent(ZonedDateTime.now());
            outboxRepository.save(m);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String reason) {
        outboxRepository.findById(id).ifPresent(m -> {
            m.markFailed(reason);
            outboxRepository.save(m);
        });
    }
}
