package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * outbox 의 미발행(PENDING) 이벤트를 주기적으로 Kafka 로 발행하는 릴레이(배달부).
 * 발행 성공을 확인한 뒤에야 PUBLISHED 로 표시한다 — "표시 전에 죽으면 재발송" 이므로
 * 유실은 없고 중복은 가능(At Least Once). 중복은 Consumer 의 멱등 처리가 걸러낸다.
 */
@Slf4j
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending(BATCH_SIZE);
        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                // payload 는 이미 JSON 문자열 — String 그대로 보내면 JsonSerializer 가 이중 인코딩하므로 트리로 변환해 보낸다.
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPartitionKey(), objectMapper.readTree(outboxEvent.getPayload()))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outboxEvent.markPublished();
                outboxEventRepository.save(outboxEvent);
            } catch (Exception e) {
                // 실패한 이벤트를 건너뛰고 뒤를 먼저 보내면 같은 파티션 키의 순서가 깨진다 — 중단하고 다음 주기에 처음부터 재시도.
                log.warn("outbox 발행 실패 — 다음 주기에 재시도합니다. eventId={}, topic={}",
                    outboxEvent.getEventId(), outboxEvent.getTopic(), e);
                break;
            }
        }
    }
}
