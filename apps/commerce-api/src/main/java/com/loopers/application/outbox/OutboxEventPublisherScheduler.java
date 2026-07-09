package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox에 적재된 PENDING 이벤트를 주기적으로 Kafka에 발행한다 (At Least Once).
 * 발행 성공 시에만 PUBLISHED로 전환하고, 실패/타임아웃 시 PENDING으로 남겨 다음 주기에 재시도한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxEventPublisherScheduler {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 3000)
    public void publishPendingEvents() {
        List<OutboxEventModel> pendingEvents = outboxEventRepository.findPending(BATCH_SIZE);
        for (OutboxEventModel event : pendingEvents) {
            publish(event);
        }
    }

    private void publish(OutboxEventModel event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            markPublished(event.getId());
        } catch (Exception e) {
            log.warn("Outbox 이벤트 발행 실패 — id={}, topic={}, key={}", event.getId(), event.getTopic(), event.getMessageKey(), e);
        }
    }

    @Transactional
    public void markPublished(Long outboxEventId) {
        outboxEventRepository.findById(outboxEventId).ifPresent(OutboxEventModel::markPublished);
    }
}
