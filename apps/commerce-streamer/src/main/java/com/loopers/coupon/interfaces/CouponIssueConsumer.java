package com.loopers.coupon.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.coupon.application.CouponIssuanceService;
import com.loopers.support.dlq.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * coupon-issue-requests 를 소비해 선착순 쿠폰을 발급한다.
 * key=couponId 라 같은 쿠폰은 한 파티션에서 단일 컨슈머가 순차 처리한다 → 수량 판정에 락이 필요 없다.
 * 역직렬화 실패(poison)는 DLT 로 격리한다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssuanceService couponIssuanceService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopic.COUPON_ISSUE_REQUESTS,
            groupId = "coupon-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            CouponIssueRequestedMessage message = deserialize(record);
            if (message != null) {
                couponIssuanceService.issue(message);
            }
        }
        acknowledgment.acknowledge();
    }

    private CouponIssueRequestedMessage deserialize(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), CouponIssueRequestedMessage.class);
        } catch (IOException e) {
            deadLetterPublisher.publish(record, e);
            return null;
        }
    }
}
