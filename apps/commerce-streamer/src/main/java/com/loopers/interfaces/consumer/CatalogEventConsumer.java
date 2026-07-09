package com.loopers.interfaces.consumer;

import com.loopers.application.catalog.CatalogEventFacade;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * catalog-events Consumer — 단건(non-batch) manual ack.
 * 처리 중 예외를 삼키지 않고 그대로 던진다: manual ack 모드에서 ack하지 않은 채 다음 메시지를 계속 처리하면
 * 이후 메시지의 ack가 이 메시지의 offset까지 앞질러 커밋해버려 실패한 메시지를 영영 건너뛰게 되는 위험이 있다.
 * 예외를 전파해야 Spring Kafka의 기본 에러 핸들러(DefaultErrorHandler)가 같은 오프셋을 재시도한다.
 */
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final CatalogEventFacade catalogEventFacade;

    @KafkaListener(topics = KafkaTopics.CATALOG_EVENTS)
    public void listen(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        String rawPayload = new String((byte[]) record.value(), StandardCharsets.UTF_8);
        catalogEventFacade.handle(rawPayload);
        acknowledgment.acknowledge();
    }
}
