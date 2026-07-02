package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
    }

    public void relayOnce() {
        List<OutboxEvent> batch = outboxRepository.findPendingBatch();
        for (OutboxEvent e : batch) {
            try {
                outboxKafkaTemplate.send(e.getTopic(), e.getMessageKey(), e.getPayload())
                    .get(5, TimeUnit.SECONDS); // 발행 확인 후 표시
                e.markSent();
                outboxRepository.save(e);
            } catch (Exception ex) {
                log.warn("outbox relay 발행 실패 eventId={} — 다음 폴에 재시도", e.getEventId(), ex);
                break; // 순서 보존: 실패분 뒤는 다음 폴로
            }
        }
    }
}
