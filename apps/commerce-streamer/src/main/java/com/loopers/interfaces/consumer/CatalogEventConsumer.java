package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.ProductMetricsApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * catalog-events 를 소비해 product_metrics 로 집계하는 collector.
 *
 * <p>record 리스너(concurrency=3, container-managed ack)로 이벤트를 한 건씩 받아, 애플리케이션 서비스가 이벤트 단위
 * 트랜잭션 + event_handled 멱등으로 반영한다. 정상 처리 시 컨테이너가 offset 을 커밋하고, 예외 시 미커밋→재전달되므로
 * at-least-once + 멱등 = effectively-once. 처리 실패는 {@link KafkaErrorHandlingConfig} 의 에러 핸들러가 재시도 후
 * {@code catalog-events.DLT} 로 격리한다(poison 이 파티션을 막지 않음).</p>
 *
 * <p>offset reset 을 earliest 로 둔다 — 집계 collector 는 유실 없이 백로그부터 따라잡아야 하기 때문(기본 latest 는
 * 신규 그룹이 구독 이전 이벤트를 건너뛴다).</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    public static final String CATALOG_EVENTS = "catalog-events";

    private final ProductMetricsApplicationService productMetricsApplicationService;

    @KafkaListener(
            topics = CATALOG_EVENTS,
            groupId = "product-metrics-collector",
            containerFactory = KafkaErrorHandlingConfig.RECORD_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consume(CatalogEventMessage message) {
        productMetricsApplicationService.apply(message);
    }
}
