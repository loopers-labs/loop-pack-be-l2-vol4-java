package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.support.config.KafkaProducerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 아웃박스 릴레이. PENDING 을 id 순으로 폴링해 단건씩 Kafka 로 발행하고, 발행이 확인되면(ack) PUBLISHED 로 마킹한다.
 * 발행 확인 전에는 마킹하지 않으므로(성공이 마킹을 게이트) 재시도 시 재발행되어 At Least Once 가 보장된다.
 * 발행 실패(브로커 장애 등) 시 그 뒤 건도 같은 이유로 실패할 가능성이 높아 루프를 멈추고 다음 주기에 재시도한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_LIMIT = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(
            OutboxRepository outboxRepository,
            @Qualifier(KafkaProducerConfig.STRING_KAFKA_TEMPLATE) KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void publishPending() {
        List<OutboxModel> pending = outboxRepository.findPending(BATCH_LIMIT);
        for (OutboxModel outbox : pending) {
            if (!publish(outbox)) {
                break;
            }
        }
    }

    private boolean publish(OutboxModel outbox) {
        try {
            kafkaTemplate.send(outbox.getTopic(), outbox.partitionKey(), outbox.getPayload()).get();
            outbox.markPublished();
            outboxRepository.save(outbox);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("아웃박스 발행 중단 - eventId={}, topic={}", outbox.getEventId(), outbox.getTopic());
            return false;
        } catch (Exception e) {
            log.warn("아웃박스 발행 실패(다음 주기 재시도) - eventId={}, topic={}: {}",
                    outbox.getEventId(), outbox.getTopic(), e.toString());
            return false;
        }
    }
}