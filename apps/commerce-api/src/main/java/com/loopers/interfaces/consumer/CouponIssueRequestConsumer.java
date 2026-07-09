package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * coupon-issue-requests Consumer — 단건(non-batch) manual ack.
 * commerce-api 자신이 coupons/user_coupons 테이블을 소유하고 있어 같은 앱 안에 Consumer를 둔다
 * (다른 앱에 두면 동일 테이블에 대한 JPA 엔티티/ddl-auto 소유권이 충돌할 위험이 있음).
 * 예외를 삼키지 않는 이유는 catalog/order-events Consumer와 동일 — 실패 메시지가 뒤 메시지 ack에 offset이 앞질러 커밋되어 유실되는 것을 방지.
 */
@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private final CouponIssueFacade couponIssueFacade;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTS)
    public void listen(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        String rawPayload = new String((byte[]) record.value(), StandardCharsets.UTF_8);
        couponIssueFacade.handle(rawPayload);
        acknowledgment.acknowledge();
    }
}
