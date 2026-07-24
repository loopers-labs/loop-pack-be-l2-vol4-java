package com.loopers.support.dlq;

import com.loopers.confg.kafka.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 처리 불가 메시지를 <topic>.DLT 로 격리한다. 원본 payload 와 사유를 헤더에 담아,
 * 컨슈머가 막히지 않게 하면서 운영자가 후처리할 수 있게 한다.
 *
 * <p>발행을 확인한 뒤에야 반환한다. {@code send()} 는 비동기라 확인 없이 반환하면
 * broker 전송 실패 시 원본은 이미 ack 됐고 DLT 에도 없어 메시지가 사라진다.
 * 확인이 안 되면 {@link DeadLetterPublishException} 을 던져 컨슈머의 ack 를 막고 원본을 재소비하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

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

        try {
            dltKafkaTemplate.send(dltRecord).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeadLetterPublishException("DLT 발행 중 인터럽트 topic=" + dltTopic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new DeadLetterPublishException(
                    "DLT 발행 실패 topic=" + dltTopic + " offset=" + record.offset() + " — 원본을 재소비한다", e);
        }
        log.warn("DLT 격리 topic={} offset={} cause={}", dltTopic, record.offset(), cause.toString());
    }

    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
