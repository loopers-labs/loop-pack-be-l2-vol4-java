package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.FirstComeIssueService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 선착순 쿠폰 발급 요청 Consumer — commerce-api 가 자기 자신의 요청을 받아 처리한다.
 * <p>
 * 파티션 키 = couponTemplateId → 같은 쿠폰의 요청들은 단일 파티션에서 <b>순차 처리</b>됨.
 * 따라서 도메인 CAS 로직만으로 초과 발급이 발생하지 않는다.
 * <p>
 * 멱등: FirstComeIssueService 가 requestId 로 조회 → 이미 종료 상태면 no-op 통과.
 */
@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueRequestConsumer.class);

    private final ObjectMapper objectMapper;
    private final FirstComeIssueService firstComeIssueService;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "commerce-api-coupon",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onBatch(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            String payload = toStringValue(record.value());
            String requestId = null;
            try {
                JsonNode node = objectMapper.readTree(payload);
                requestId = node.get("requestId").asText();
                FirstComeIssueService.IssueResult result = firstComeIssueService.processRequest(requestId);
                log.info("쿠폰 발급 처리 완료 — requestId={}, status={}", requestId, result.status());
            } catch (Exception e) {
                log.error("쿠폰 발급 처리 실패 — requestId={}, offset={}, cause={}",
                    requestId, record.offset(), e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
        ack.acknowledge();
    }

    private static String toStringValue(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value.toString();
    }
}
