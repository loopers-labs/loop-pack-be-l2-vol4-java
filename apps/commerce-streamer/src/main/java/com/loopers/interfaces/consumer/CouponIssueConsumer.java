package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueRequestMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
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
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, byte[]> record = records.get(i);
            try {
                CouponIssueRequestMessage msg = objectMapper.readValue(record.value(), CouponIssueRequestMessage.class);
                couponIssueProcessor.handle(msg); // 처리 실패 전파 → 재시도(멱등) → 소진 시 DLT
            } catch (Exception e) {
                throw new BatchListenerFailedException("coupon-issue-requests 처리 실패 offset=" + record.offset(), e, i);
            }
        }
        ack.acknowledge();
    }
}
