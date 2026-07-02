package com.loopers.tddstudy.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.tddstudy.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.tddstudy.infrastructure.outbox.OutboxMessage;
import com.loopers.tddstudy.messaging.CatalogEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxJpaRepository outboxRepository,
                       KafkaTemplate<Object, Object> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publish() {
        List<OutboxMessage> batch = outboxRepository.findTop100ByStatusOrderByIdAsc("INIT");
        for (OutboxMessage msg : batch) {
            try {
                CatalogEvent event = objectMapper.readValue(msg.getPayload(), CatalogEvent.class);
                // key=partitionKey(productId) → 같은 상품은 같은 파티션 = 순서 보장
                kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), event).get();
                msg.markSent();                       // 발행 성공 확인 후 SENT
            } catch (Exception ex) {
                break;   // 실패 시 INIT 유지 → 다음 주기 재시도 (순서 위해 중단)
            }
        }
    }
}
