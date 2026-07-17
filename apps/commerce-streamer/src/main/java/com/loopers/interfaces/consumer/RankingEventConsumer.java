package com.loopers.interfaces.consumer;

import com.loopers.application.ranking.RankingCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * catalog-events + order-events 를 raw 랭킹 신호 보드로 수집하는 collector (Collect 구간).
 *
 * <p>metrics collector 와 <b>같은 토픽, 다른 consumer group</b> — projection 별 그룹 분리로 offset·실패·재시도가
 * 완전히 독립된다. Redis 장애 시 이 그룹만 offset 미커밋으로 정지(랙 = 무손실 버퍼)하고 metrics 는 무영향,
 * 복구 후 랙을 따라잡아 유실 0. metrics apply() 에 편승하면 공유 dedup 이 재시도를 흡수해 이 성질을 잃는다.</p>
 *
 * <p>에러 정책은 {@link KafkaErrorHandlingConfig#RANKING_RECORD_LISTENER} — 일시 장애 무한 재시도 / poison skip.
 * 멱등 장치는 두지 않는다: 재전달 이중 가산은 근사 예산이고, 대가로 group offset 리셋만으로 보드 재구축이 가능하다.</p>
 */
@Component
@RequiredArgsConstructor
public class RankingEventConsumer {

    public static final String GROUP_ID = "product-ranking-collector";

    private final RankingCollectService rankingCollectService;

    @KafkaListener(
            topics = CatalogEventConsumer.CATALOG_EVENTS,
            groupId = GROUP_ID,
            containerFactory = KafkaErrorHandlingConfig.RANKING_RECORD_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consumeCatalog(CatalogEventMessage message) {
        rankingCollectService.collect(message);
    }

    @KafkaListener(
            topics = OrderSalesConsumer.ORDER_EVENTS,
            groupId = GROUP_ID,
            containerFactory = KafkaErrorHandlingConfig.RANKING_RECORD_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consumeOrder(OrderEventMessage message) {
        rankingCollectService.collect(message);
    }
}
