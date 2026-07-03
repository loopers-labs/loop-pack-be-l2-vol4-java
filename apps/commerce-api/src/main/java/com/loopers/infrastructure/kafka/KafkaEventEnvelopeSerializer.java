package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// outbox 레코드 또는 이벤트 객체를 KafkaEventEnvelope JSON으로 직렬화한다.
@RequiredArgsConstructor
@Component
public class KafkaEventEnvelopeSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(OutboxModel outbox) {
        return serialize(outbox.getEventId(), outbox.getAggregateType(), outbox.getAggregateId(),
                outbox.getEventType(), readTree(outbox.getPayload()));
    }

    public String serialize(String eventId, String aggregateType, String aggregateId, String eventType, Object rawEvent) {
        return serialize(eventId, aggregateType, aggregateId, eventType, objectMapper.valueToTree(rawEvent));
    }

    private String serialize(String eventId, String aggregateType, String aggregateId, String eventType, JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(new KafkaEventEnvelope(eventId, aggregateType, aggregateId, eventType, payload));
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "kafka 이벤트 봉투 직렬화에 실패했습니다.");
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "outbox payload 파싱에 실패했습니다.");
        }
    }
}
