package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Transactional Outbox 의 Relay — 미발행 outbox 행을 폴링해 Kafka 로 발행하고 published 로 마킹한다.
 * 행마다 try/catch 로 격리: 한 건 발행 실패가 배치 전체를 막지 않고, 실패 건은 미발행으로 남아 다음 폴링에 재발행된다(At Least Once).
 * @Scheduled 트리거는 {@code OutboxRelayScheduler} 가 담당(이 빈은 트랜잭션·발행 로직만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublished(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                publishOne(event);
            } catch (Exception e) {
                log.warn("outbox 발행 실패 — 다음 폴링에 재시도 (eventId={})", event.getEventId(), e);
            }
        }
    }

    private void publishOne(OutboxEvent event) {
        String topic = resolveTopic(event.getAggregateType());
        String key = String.valueOf(event.getAggregateId());   // StringSerializer 호환 + 같은 상품 = 같은 파티션(순서 보장)
        OutboxMessage message = toMessage(event);              // 봉투로 감싸 eventId 까지 Consumer 로 전달(self-contained)
        try {
            // send 는 비동기. .get() 으로 배달 확인(ack)까지 동기 대기 — 확인된 뒤에만 마킹해야 At Least Once.
            kafkaTemplate.send(topic, key, message).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox 발행 중 인터럽트 (eventId=" + event.getEventId() + ")", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("outbox 발행 실패 (eventId=" + event.getEventId() + ")", e.getCause());
        }
        // 여기 도달 = 발행 확인됨. relay() 가 @Transactional 이라 markPublished() 만 하면 커밋 때 더티체킹으로 UPDATE.
        event.markPublished();
    }

    private OutboxMessage toMessage(OutboxEvent event) {
        try {
            // payload(JSON 문자열)를 노드로 파싱해 봉투의 data 에 중첩 — 메타(봉투)와 도메인(본문) 분리 유지.
            JsonNode data = objectMapper.readTree(event.getPayload());
            return new OutboxMessage(event.getEventId(), event.getEventType(), event.getAggregateId(), data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload 파싱 실패 (eventId=" + event.getEventId() + ")", e);
        }
    }

    private String resolveTopic(String aggregateType) {
        return switch (aggregateType) {
            case "Product" -> "catalog-events";
            default -> throw new IllegalStateException("매핑되지 않은 aggregateType: " + aggregateType);
        };
    }
}
