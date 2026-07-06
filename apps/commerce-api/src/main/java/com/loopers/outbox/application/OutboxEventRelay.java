package com.loopers.outbox.application;

import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 순수 폴러 방식 Outbox 릴레이. PENDING 메시지를 잠금 조회해 발행하고 PUBLISHED 로 표시한다.
 * - 발행 실패 행은 PENDING 으로 남아 다음 폴에서 재시도된다(At Least Once → consumer 멱등 필수).
 * - 한 행의 발행 실패가 같은 배치의 다른 행 발행을 막지 않는다.
 */
@Slf4j
@Component
public class OutboxEventRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessagePublisher publisher;
    private final int batchSize;

    public OutboxEventRelay(OutboxEventRepository outboxEventRepository,
                            OutboxMessagePublisher publisher,
                            @Value("${outbox.relay.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relay() {
        List<OutboxEvent> pendings = outboxEventRepository.findPendingForUpdate(batchSize);
        for (OutboxEvent event : pendings) {
            try {
                publisher.publish(event.getTopic(), event.getMessageKey(), event.getPayload());
                event.markPublished();
            } catch (Exception e) {
                log.warn("outbox 발행 실패 — PENDING 유지 후 재시도 outboxId={} topic={} key={}",
                        event.getId(), event.getTopic(), event.getMessageKey(), e);
            }
        }
    }
}
