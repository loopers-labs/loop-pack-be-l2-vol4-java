package com.loopers.support.dlq;

import com.loopers.confg.kafka.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 처리 불가 메시지를 <topic>.DLT 로 격리한다. 원본 payload 와 사유를 헤더에 담아,
 * 컨슈머가 막히지 않게 하면서 운영자가 후처리할 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private final KafkaTemplate<String, String> dltKafkaTemplate;

    public void publish(ConsumerRecord<String, byte[]> record, Exception cause) {
        String dltTopic = KafkaTopic.deadLetterOf(record.topic());
        String payload = record.value() == null ? "" : new String(record.value(), StandardCharsets.UTF_8);

        ProducerRecord<String, String> dltRecord = new ProducerRecord<>(dltTopic, record.key(), payload);
        addHeader(dltRecord, DlqHeaders.ORIGINAL_TOPIC, record.topic());
        addHeader(dltRecord, DlqHeaders.ORIGINAL_PARTITION, String.valueOf(record.partition()));
        addHeader(dltRecord, DlqHeaders.ORIGINAL_OFFSET, String.valueOf(record.offset()));
        addHeader(dltRecord, DlqHeaders.EXCEPTION_CLASS, cause.getClass().getName());
        addHeader(dltRecord, DlqHeaders.EXCEPTION_MESSAGE, String.valueOf(cause.getMessage()));

        dltKafkaTemplate.send(dltRecord);
        log.warn("DLT 격리 topic={} offset={} cause={}", dltTopic, record.offset(), cause.toString());
    }

    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
