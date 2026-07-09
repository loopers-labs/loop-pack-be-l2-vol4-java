package com.loopers.application.metrics;

import com.loopers.domain.eventlog.EventHandled;
import com.loopers.domain.eventlog.EventHandledId;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.eventlog.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.interfaces.consumer.CatalogEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 이벤트 한 건을 product_metrics 로 멱등하게 반영한다.
 *
 * <p>event_handled 판정 + INSERT 와 product_metrics 증감을 <b>한 트랜잭션</b>으로 묶는다(결정 ④). 이미 처리한
 * event_id 면 아무 것도 하지 않는다 → at-least-once 재전달을 흡수. 같은 상품 이벤트는 파티션 직렬화로 단일 스레드만
 * 처리하므로 find→증감→save 에 락이 필요 없다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductMetricsApplicationService {

    /** event_handled 멱등 스코프 식별자. 원장을 다른 소비자와 공유하되 스코프는 handler 로 분리된다. */
    public static final String HANDLER = "product-metrics";

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public void apply(CatalogEventMessage message) {
        EventHandledId handledId = new EventHandledId(message.eventId(), HANDLER);
        if (eventHandledJpaRepository.existsById(handledId)) {
            return; // 이미 처리한 이벤트 — 중복 흡수
        }

        ProductMetrics metrics = productMetricsJpaRepository.findById(message.productId())
                .orElseGet(() -> ProductMetrics.of(message.productId()));

        switch (message.type()) {
            case PRODUCT_LIKED -> metrics.increaseLike();
            case PRODUCT_UNLIKED -> metrics.decreaseLike();
            case PRODUCT_VIEWED -> metrics.increaseView();
        }

        productMetricsJpaRepository.save(metrics);
        eventHandledJpaRepository.save(EventHandled.of(message.eventId(), HANDLER));
    }
}
