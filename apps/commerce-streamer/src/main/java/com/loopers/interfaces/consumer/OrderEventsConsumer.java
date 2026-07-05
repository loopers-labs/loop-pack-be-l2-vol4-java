package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderEventFacade;
import com.loopers.application.order.OrderEventMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final ObjectMapper objectMapper;
    private final OrderEventFacade orderEventFacade;

    @KafkaListener(topics = "order-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        List<OrderEventMessage> messages = records.stream()
            .map(this::parse)
            .toList();
        orderEventFacade.handle(messages);
        acknowledgment.acknowledge();   // manual ack: 처리 성공 후에만 오프셋 커밋(실패 시 재전송 → 멱등이 흡수)
    }

    private OrderEventMessage parse(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), OrderEventMessage.class);
        } catch (IOException e) {
            throw new IllegalStateException("order-events 역직렬화 실패 (offset=" + record.offset() + ")", e);
        }
    }
}
