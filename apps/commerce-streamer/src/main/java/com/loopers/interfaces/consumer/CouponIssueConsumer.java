package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueRequestMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final ObjectMapper objectMapper;
    private final CouponIssueProcessor couponIssueProcessor;

    @KafkaListener(topics = "coupon-issue-requests", groupId = "coupon-issue", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, byte[]> record : records) {
            CouponIssueRequestMessage msg;
            try {
                msg = objectMapper.readValue(record.value(), CouponIssueRequestMessage.class);
            } catch (Exception e) {
                log.error("coupon-issue-requests 파싱 실패(skip) offset={}", record.offset(), e);
                continue;
            }
            couponIssueProcessor.handle(msg); // 처리 실패 전파 → 배치 미-ack → 재전달(멱등)
        }
        ack.acknowledge();
    }
}
