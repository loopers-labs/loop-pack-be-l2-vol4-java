package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.MetricsEventHandler;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * commerce-api 이벤트 소비 — product_metrics 집계 파이프라인.
 *
 * <p>배치 리스너({@link KafkaConfig#BATCH_LISTENER}) + <strong>manual ack</strong>:
 * 배치 전체를 처리한 뒤에만 오프셋을 커밋한다. 처리 중 예외가 나면 ack 없이 전파되어
 * 배치가 재전달된다 — 이미 처리된 건은 {@code event_handled} 멱등 가드가 걸러내므로
 * 재전달이 중복 집계로 이어지지 않는다.
 */
@RequiredArgsConstructor
@Component
public class CommerceEventConsumer {

    private final MetricsEventHandler metricsEventHandler;

    @KafkaListener(
        topics = {
            "${commerce-events.topics.catalog}",
            "${commerce-events.topics.order}",
            "${commerce-events.topics.view}"
        },
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onCommerceEvents(List<EventEnvelope> messages, Acknowledgment acknowledgment) {
        for (EventEnvelope envelope : messages) {
            metricsEventHandler.handle(envelope);
        }
        acknowledgment.acknowledge();
    }
}
