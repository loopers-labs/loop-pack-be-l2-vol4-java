package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.outbox.EventEnvelope;
import com.loopers.infrastructure.outbox.OutboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

/**
 * outbox 행 하나를 {@link EventEnvelope}로 감싸 Kafka로 발행하고 브로커 ack까지 확인하는 <b>공통 전송 로직</b>.
 *
 * <p>주기 폴링 릴레이({@link OutboxRelay})와 커밋 직후 즉시 발행({@link OutboxImmediatePublisher})이 모두
 * 이 컴포넌트를 재사용한다. 상태 마킹({@code markSent})·트랜잭션·재시도(PENDING 유지) 정책은 호출자 책임이며,
 * 이 클래스는 "봉투로 감싸 {@code send().get()}으로 ack까지 확인"만 담당한다(부수효과 없음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessagePublisher {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 메시지를 발행하고 브로커 ack까지 동기 대기한다. ack 실패/타임아웃 시 예외를 던진다
     * (호출자가 {@code markSent}를 보류하고 PENDING으로 남겨 재발행되게 한다).
     */
    public void publish(OutboxEntity msg) throws ExecutionException, InterruptedException {
        EventEnvelope envelope = toEnvelope(msg);
        kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), envelope).get();
    }

    private EventEnvelope toEnvelope(OutboxEntity msg) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(msg.getPayload());
        } catch (Exception e) {
            payload = objectMapper.createObjectNode();
            log.error("outbox payload 파싱 실패(eventId={}): {}", msg.getId(), e.getMessage());
        }
        return new EventEnvelope(
                msg.getId(),
                msg.getEventType(),
                msg.getAggregateType(),
                msg.getAggregateId(),
                msg.getVersion(),
                msg.getCreatedAt().format(ISO),
                payload
        );
    }
}
