package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox → Kafka 발행기(polling relay).
 * <p>
 * 주기적으로 PENDING outbox 행을 잠금 조회(FOR UPDATE SKIP LOCKED)해 Kafka로 전송하고 PUBLISHED로 표시한다.
 * <ul>
 *   <li>SKIP LOCKED: 여러 인스턴스가 동시에 폴링해도 같은 행을 잡지 않아 중복 발행을 줄인다.</li>
 *   <li>send().get(): 브로커 ack까지 동기 대기. 실패하면 예외 → 트랜잭션 롤백 → 행은 PENDING으로 남아 다음 폴링에 재시도(at-least-once).</li>
 *   <li>재시도로 인한 중복은 컨슈머의 event_id 멱등 처리로 흡수한다.</li>
 * </ul>
 * {@code outbox.relay.enabled=false}면 비활성화된다(브로커 없는 단위 테스트용).
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    public OutboxRelay(
        OutboxEventRepository outboxEventRepository,
        @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    @Transactional
    public void dispatch() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForDispatch(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                // ack까지 동기 대기: 실패 시 예외로 트랜잭션을 롤백해 미발행 상태를 유지한다.
                outboxKafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("outbox 발행 중 인터럽트: " + event.getEventId(), e);
            } catch (Exception e) {
                throw new IllegalStateException("outbox 발행 실패, 다음 폴링에 재시도: " + event.getEventId(), e);
            }
        }
        log.debug("outbox {}건 발행 완료", pending.size());
    }
}
