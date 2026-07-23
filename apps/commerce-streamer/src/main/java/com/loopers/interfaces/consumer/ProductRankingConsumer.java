package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.EventEnvelope;
import com.loopers.application.ranking.RankingAggregator;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * catalog-events(LIKE_CHANGED/PRODUCT_VIEWED) + order-events(ORDER_PAID)를 배치로 받아
 * 일간 랭킹 ZSET 에 가중 점수를 누적한다.
 *
 * <p>{@link RankingAggregator#CONSUMER_GROUP} 은 product_metrics 집계({@code metrics-aggregator})와 다른
 * 컨슈머 그룹이라, 같은 토픽을 독립 오프셋으로 소비한다(랭킹 Redis 파이프라인 ⟂ 측정값 DB 파이프라인).
 * ZSET 반영이 끝난 뒤 수동 커밋(at-least-once)한다.
 */
@Component
@RequiredArgsConstructor
public class ProductRankingConsumer {

    private final RankingAggregator rankingAggregator;

    @KafkaListener(
            topics = {"${event-topics.catalog}", "${event-topics.order}"},
            groupId = RankingAggregator.CONSUMER_GROUP,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<EventEnvelope> envelopes, Acknowledgment acknowledgment) {
        rankingAggregator.apply(envelopes);
        acknowledgment.acknowledge();
    }
}
