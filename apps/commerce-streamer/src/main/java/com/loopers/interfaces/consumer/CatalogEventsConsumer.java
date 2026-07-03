package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// 단건 처리, 기본 컨테이너 팩토리(spring.kafka.listener.ack-mode: manual가 전역 적용됨) 사용.
// consumer.value-deserializer가 ByteArrayDeserializer이므로 레코드 값은 byte[]다.
// 예외 발생 시 ack를 호출하지 않아 다음 poll에서 재전달되도록 한다(DLQ는 스코프 아님).
@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventsConsumer {

    private final MetricsFacade metricsFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(id = "catalogEventsConsumer", topics = "catalog-events", groupId = "metrics-consumer")
    public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        try {
            KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
            Long productId = Long.valueOf(envelope.aggregateId());
            switch (envelope.eventType()) {
                case "PRODUCT_LIKED" -> metricsFacade.applyLike(envelope.eventId(), productId);
                case "PRODUCT_UNLIKED" -> metricsFacade.applyUnlike(envelope.eventId(), productId);
                case "PRODUCT_VIEWED" -> metricsFacade.applyView(envelope.eventId(), productId);
                default -> log.warn("처리할 수 없는 이벤트 타입입니다. eventType={}", envelope.eventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("catalog-events 처리 실패. key={}", record.key(), e);
        }
    }
}
