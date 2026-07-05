package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.application.coupon.CouponIssueMessage;
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
public class CouponIssueRequestsConsumer {

    private final ObjectMapper objectMapper;
    private final CouponIssueFacade couponIssueFacade;

    @KafkaListener(topics = "coupon-issue-requests", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        List<CouponIssueMessage> messages = records.stream()
            .map(this::parse)
            .toList();
        couponIssueFacade.handle(messages);
        acknowledgment.acknowledge();   // 처리 끝난 뒤에만 커밋 — 실패 시 미커밋 → 배치 재전송 → 멱등이 흡수
    }

    private CouponIssueMessage parse(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), CouponIssueMessage.class);
        } catch (IOException e) {
            throw new IllegalStateException("coupon-issue-requests 역직렬화 실패 (offset=" + record.offset() + ")", e);
        }
    }
}
