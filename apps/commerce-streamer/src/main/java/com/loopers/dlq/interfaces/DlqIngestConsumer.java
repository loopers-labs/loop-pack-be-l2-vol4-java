package com.loopers.dlq.interfaces;

import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.dlq.domain.DlqMessage;
import com.loopers.dlq.infrastructure.DlqMessageJpaRepository;
import com.loopers.support.dlq.DlqHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 모든 .DLT 토픽을 구독해 격리 메시지를 dlq_message 테이블로 적재한다(조회·재처리 가능하게).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqIngestConsumer {

    private final DlqMessageJpaRepository dlqMessageJpaRepository;

    @KafkaListener(topicPattern = ".*\\.DLT", groupId = "dlq-ingest", containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void ingest(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            dlqMessageJpaRepository.save(DlqMessage.of(
                    header(record, DlqHeaders.ORIGINAL_TOPIC),
                    intHeader(record, DlqHeaders.ORIGINAL_PARTITION),
                    longHeader(record, DlqHeaders.ORIGINAL_OFFSET),
                    record.key(),
                    record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8),
                    header(record, DlqHeaders.EXCEPTION_CLASS),
                    header(record, DlqHeaders.EXCEPTION_MESSAGE)
            ));
        }
        acknowledgment.acknowledge();
    }

    private String header(ConsumerRecord<String, byte[]> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Integer intHeader(ConsumerRecord<String, byte[]> record, String key) {
        String value = header(record, key);
        return value == null ? null : Integer.valueOf(value);
    }

    private Long longHeader(ConsumerRecord<String, byte[]> record, String key) {
        String value = header(record, key);
        return value == null ? null : Long.valueOf(value);
    }
}
