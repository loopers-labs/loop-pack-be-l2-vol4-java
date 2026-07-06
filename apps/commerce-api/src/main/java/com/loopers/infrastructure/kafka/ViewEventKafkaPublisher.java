package com.loopers.infrastructure.kafka;

import com.loopers.application.activity.ProductViewedEvent;
import com.loopers.confg.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상품 조회 이벤트 Kafka 직접 발행 — <strong>의도적으로 Outbox 를 거치지 않는다.</strong>
 *
 * <p>조회는 고빈도·유실 허용(집계용 근사치) 데이터라, 조회마다 outbox INSERT 를 하면
 * 읽기 경로에 쓰기 부하가 생겨 배보다 배꼽이 커진다. 좋아요/결제(유실 불가)만 Outbox 로 보장하고,
 * 조회는 fire-and-forget 으로 직접 발행한다 — 이벤트 중요도에 따른 전송 보장 차등.
 *
 * <p>{@code @Async} — Kafka 브로커 지연/장애가 조회 응답에 영향을 주지 않도록 요청 스레드와 분리.
 * 전송 결과도 기다리지 않는다(producer 내부 배칭·재시도에 위임).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ViewEventKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${commerce-events.topics.view}")
    private String viewTopic;

    @Async
    @EventListener
    public void onProductViewed(ProductViewedEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", event.productId());
            payload.put("userId", event.userId());   // 비로그인 조회는 null

            EventEnvelope envelope = EventEnvelope.of("PRODUCT_VIEWED", payload);
            kafkaTemplate.send(viewTopic, event.productId().toString(), envelope);
        } catch (Exception e) {
            // 조회 이벤트는 유실 허용 — 로그만 남긴다
            log.warn("[ViewEvent] 조회 이벤트 발행 실패 — 유실 허용. productId={}", event.productId(), e);
        }
    }
}
