package com.loopers.interfaces.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxModel> pending = outboxRepository.findPendingBatch(BATCH_SIZE);
        for (OutboxModel outbox : pending) {
            try {
                kafkaTemplate.send(outbox.getTopic(), outbox.getPartitionKey(), withOutboxId(outbox)).get();
                outbox.markPublished();
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("[OutboxRelay] 발행 실패: outboxId={}, topic={}", outbox.getId(), outbox.getTopic(), e);
            }
        }
    }

    private String withOutboxId(OutboxModel outbox) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(outbox.getPayload());
            root.put("outboxId", outbox.getId().toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("outboxId 추가 실패: outboxId=" + outbox.getId(), e);
        }
    }
}
