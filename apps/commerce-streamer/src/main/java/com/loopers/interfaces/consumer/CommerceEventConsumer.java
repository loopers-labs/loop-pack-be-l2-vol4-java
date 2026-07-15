package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.MetricsBatchEventHandler;
import com.loopers.application.ranking.RankingContribution;
import com.loopers.confg.kafka.message.EventEnvelope;
import com.loopers.config.MetricsKafkaListenerConfig;
import com.loopers.infrastructure.ranking.RankingRedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * commerce-api 이벤트 소비 — product_metrics 집계 + 실시간 랭킹 파이프라인.
 *
 * <p>배치 리스너({@link MetricsKafkaListenerConfig#METRICS_BATCH_LISTENER}) + <strong>manual ack</strong>:
 * 배치를 productId 로 합산해 (1) DB 집계를 트랜잭션으로 반영하고 (2) 커밋 후 랭킹 ZSET 을 갱신한 뒤 ack 한다.
 * 처리 중 예외가 나면 ack 없이 배치가 재전달되며, 이미 처리된 건은 {@code event_handled} 멱등 가드가 걸러낸다.
 */
@RequiredArgsConstructor
@Component
public class CommerceEventConsumer {

    private final MetricsBatchEventHandler metricsBatchEventHandler;
    private final RankingRedisStore rankingRedisStore;

    @KafkaListener(
        topics = {
            "${commerce-events.topics.catalog}",
            "${commerce-events.topics.order}",
            "${commerce-events.topics.view}"
        },
        containerFactory = MetricsKafkaListenerConfig.METRICS_BATCH_LISTENER
    )
    public void onCommerceEvents(List<EventEnvelope> messages, Acknowledgment acknowledgment) {
        Map<Long, RankingContribution> contributions = metricsBatchEventHandler.aggregateAndPersist(messages);
        rankingRedisStore.incrementScores(contributions);   // 커밋 후 랭킹 반영(fail-open)
        acknowledgment.acknowledge();
    }
}
