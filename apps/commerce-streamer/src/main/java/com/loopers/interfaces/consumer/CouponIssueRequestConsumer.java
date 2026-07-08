package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueRequestMessage;
import com.loopers.application.coupon.FirstComeCouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private final ObjectMapper objectMapper;
    private final FirstComeCouponIssueProcessor firstComeCouponIssueProcessor;

    @KafkaListener(
        topics = {"${loopers.kafka.topics.coupon-issue-requests}"},
        groupId = "${loopers.kafka.consumer-groups.coupon-issue}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, Object>> records, Acknowledgment acknowledgment) throws IOException {
        for (ConsumerRecord<String, Object> record : records) {
            CouponIssueRequestMessage event = objectMapper.readValue(payload(record.value()), CouponIssueRequestMessage.class);
            firstComeCouponIssueProcessor.process(event);
        }
        acknowledgment.acknowledge();
    }

    private String payload(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
