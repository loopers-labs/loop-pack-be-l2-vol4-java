package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 발급 요청을 순차 소비해 처리한다.
 * key=couponId 라 같은 쿠폰의 요청은 같은 파티션에서 도착 순서대로 처리된다 — 선착순의 "순서"가 여기서 보장된다.
 * 요청 상태(PENDING 여부)가 멱등 키 역할을 하므로 재배달돼도 결과는 한 번만 반영된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"coupon-issue-requests"},
        groupId = "coupon-issuer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "${coupon-consumer.auto-startup:true}"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                JsonNode envelope = objectMapper.readTree(record.value().toString());
                couponIssueProcessor.process(envelope.get("payload").get("requestId").asText());
            } catch (Exception e) {
                // 실패 시 ack 하지 않고 예외 전파 → 배치 재배달. 처리된 앞부분은 요청 상태(멱등)가 걸러낸다.
                log.error("쿠폰 발급 처리 실패 — 배치를 ack 하지 않고 재시도합니다. offset={}", record.offset(), e);
                throw new IllegalStateException("쿠폰 발급 처리 실패", e);
            }
        }
        acknowledgment.acknowledge();
    }
}
