package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.activity.UserActivityEvent;
import com.loopers.application.outbox.OutboxMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 상품 조회수 집계용 브리지. 조회는 유실돼도 무해한 지표라, outbox(At-Least-Once, 매 조회마다 DB write)를 쓰지 않고
 * catalog-events 로 fire-and-forget(At-Most-Once) 발행한다 → read 경로의 @Cacheable 최적화를 해치지 않는다.
 * (좋아요·판매량이 outbox 로 정확히 세는 것과 대비되는, '지표 특성에 맞춘 전달 보장' 선택.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewCountPublisher {

    private static final String TOPIC = "catalog-events";
    private static final String EVENT_TYPE = "VIEWED";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @Async: 조회 요청 스레드를 막지 않도록 별도 스레드에서 발행한다(read latency 를 Kafka 에 묶지 않는다).
     * try/catch: send() 가 동기적으로 던지는 예외(메타데이터 타임아웃 등)까지 삼켜, 조회수 발행 실패가
     * 조회 자체에 절대 영향을 주지 않게 한다(At-Most-Once, 유실 허용).
     */
    @Async
    @EventListener
    public void on(UserActivityEvent event) {
        if (event.type() != UserActivityEvent.Type.PRODUCT_VIEWED) {
            return;
        }
        Long productId = event.targetId();
        try {
            OutboxMessage message = new OutboxMessage(
                UUID.randomUUID().toString(), EVENT_TYPE, productId, objectMapper.createObjectNode());
            kafkaTemplate.send(TOPIC, String.valueOf(productId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("조회수 이벤트 발행 실패 — 유실 허용 (productId={})", productId, ex);
                    }
                });
        } catch (Exception e) {
            log.warn("조회수 이벤트 발행 실패(동기) — 유실 허용 (productId={})", productId, e);
        }
    }
}
