package com.loopers.interfaces.consumer;

import com.loopers.application.order.OrderEventFacade;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * order-events Consumer — 단건(non-batch) manual ack.
 * 예외를 삼키지 않는 이유는 CatalogEventConsumer와 동일 (실패 메시지의 offset이 뒤 메시지에 앞질러 커밋되는 것을 방지).
 */
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final OrderEventFacade orderEventFacade;

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
    public void listen(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        String rawPayload = new String((byte[]) record.value(), StandardCharsets.UTF_8);
        orderEventFacade.handle(rawPayload);
        acknowledgment.acknowledge();
    }
}
