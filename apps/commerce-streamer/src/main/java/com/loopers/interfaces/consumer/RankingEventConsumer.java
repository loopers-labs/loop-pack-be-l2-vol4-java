package com.loopers.interfaces.consumer;

import com.loopers.application.ranking.RankingCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * catalog-events + order-events 를 raw 랭킹 신호 보드로 수집하는 collector (Collect 구간).
 *
 * <p>metrics collector 와 <b>같은 토픽, 다른 consumer group</b> — projection 별 그룹 분리로 offset·실패·재시도가
 * 완전히 독립된다. Redis 장애 시 이 그룹만 offset 미커밋으로 정지(랙 = 무손실 버퍼)하고 metrics 는 무영향,
 * 복구 후 랙을 따라잡아 유실 0. metrics apply() 에 편승하면 공유 dedup 이 재시도를 흡수해 이 성질을 잃는다.</p>
 *
 * <p><b>배치 리스너</b>: 폴링된 배치를 리스너 안에서 (신호, 날짜, 상품) 슬롯으로 합산해 파이프라인 1회로 쓴다.
 * 건당 동기 왕복이 사라지므로 백로그 드레인(장애 복구·offset 리셋 재구축)이 수 배 빨라지고, 같은 상품에
 * 몰린 이벤트는 명령 하나로 합쳐져 Redis 부하도 준다. 실시간 유입(배치에 1~2건)에선 단건 처리와 등가.</p>
 *
 * <p>에러 정책은 {@link KafkaErrorHandlingConfig#RANKING_BATCH_LISTENER} — 일시 장애(Redis)는 배치 전체
 * 무한 재시도, 매핑 poison 은 서비스가 요소 단위로 skip 한다(한 요소가 배치를 재시도에 가두지 않게).
 * 멱등 장치는 두지 않는다: 재전달 이중 가산은 근사 예산이고, 대가로 group offset 리셋만으로 보드 재구축이 가능하다.</p>
 */
@Component
@RequiredArgsConstructor
public class RankingEventConsumer {

    public static final String GROUP_ID = "product-ranking-collector";

    /**
     * 그룹 id 는 프로퍼티로 재정의 가능(기본 = {@link #GROUP_ID}). 프로덕션에서 바꿀 일은 없고,
     * 통합 테스트가 캐시된 다른 컨텍스트와 같은 그룹을 나눠 갖는 리밸런스 경합을 피하기 위한 이음새다.
     */
    public static final String GROUP_ID_PROPERTY = "${ranking.consumer-group:" + GROUP_ID + "}";

    private final RankingCollectService rankingCollectService;

    @KafkaListener(
            topics = CatalogEventConsumer.CATALOG_EVENTS,
            groupId = GROUP_ID_PROPERTY,
            containerFactory = KafkaErrorHandlingConfig.RANKING_BATCH_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consumeCatalog(List<CatalogEventMessage> messages) {
        rankingCollectService.collectCatalogBatch(messages);
    }

    @KafkaListener(
            topics = OrderSalesConsumer.ORDER_EVENTS,
            groupId = GROUP_ID_PROPERTY,
            containerFactory = KafkaErrorHandlingConfig.RANKING_BATCH_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consumeOrder(List<OrderEventMessage> messages) {
        rankingCollectService.collectOrderBatch(messages);
    }
}
