package com.loopers.product.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 조회 이벤트를 catalog-events 로 best-effort 발행한다.
 * - 트랜잭션 없는 읽기라 Outbox 대신 직접 발행, @Async 로 조회 응답을 막지 않음.
 * - 발행 실패는 무시(로그만). 조회 수는 정확성보다 가용성이 중요한 근사 지표.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogViewPublisher {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void on(ProductViewedEvent event) {
        try {
            CatalogEventMessage message = CatalogEventMessage.view(event.productId());
            outboxKafkaTemplate.send(KafkaTopic.CATALOG_EVENTS,
                    String.valueOf(event.productId()), objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("조회 이벤트 발행 실패(무시) productId={}", event.productId(), e);
        }
    }
}
