package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.application.metrics.ProductMetricsService.SalesLine;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * catalog-events / order-events를 소비해 product_metrics를 집계하는 컨슈머.
 * <p>
 * 배치 리스너 + 수동 ack. 각 메시지는 event_id로 멱등 처리되며, 배치 전체를 처리한 뒤 ack한다.
 * 처리 도중 예외로 ack하지 못하면 배치가 재전송되고, 이미 반영된 건은 event_handled로 걸러진다(at-least-once).
 */
@RequiredArgsConstructor
@Component
public class ProductMetricsConsumer {

    private static final String GROUP_ID = "product-metrics";

    private final ProductMetricsService productMetricsService;

    @KafkaListener( // catalog-events 소비
        topics = Topics.CATALOG_EVENTS,
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onCatalogEvents(List<CatalogEventMessage> messages, Acknowledgment acknowledgment) {
        for (CatalogEventMessage message : messages) {
            long likeDelta = "LIKED".equals(message.type()) ? 1L : -1L;
            productMetricsService.applyLike(message.eventId(), message.productId(), likeDelta);
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener( // order-events 소비
        topics = Topics.ORDER_EVENTS,
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onOrderEvents(List<OrderEventMessage> messages, Acknowledgment acknowledgment) {
        for (OrderEventMessage message : messages) {
            List<SalesLine> lines = message.lines().stream()
                .map(l -> new SalesLine(l.productId(), l.quantity()))
                .toList();
            productMetricsService.applySales(message.eventId(), lines);
        }
        acknowledgment.acknowledge();
    }
}
