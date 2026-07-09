package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.EventEnvelope;
import com.loopers.application.metrics.MetricsAggregator;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * catalog-events(LIKE_CHANGED/PRODUCT_VIEWED) + order-events(ORDER_PAID)를 배치로 받아
 * product_metrics 측정값에 집계 반영한다.
 *
 * <p>수동 커밋(at-least-once): 트랜잭션 집계({@link MetricsAggregator#apply})가 <b>커밋된 뒤에만</b> ack 한다.
 * 장애로 배치가 재전달되면 event_handled 멱등으로 이중 반영이 차단된다.
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private final MetricsAggregator metricsAggregator;

    @KafkaListener(
            topics = {"${event-topics.catalog}", "${event-topics.order}"},
            groupId = MetricsAggregator.CONSUMER_GROUP,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<EventEnvelope> envelopes, Acknowledgment acknowledgment) {
        metricsAggregator.apply(envelopes);
        acknowledgment.acknowledge();
    }
}
