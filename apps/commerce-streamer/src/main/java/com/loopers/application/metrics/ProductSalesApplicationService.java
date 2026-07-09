package com.loopers.application.metrics;

import com.loopers.domain.eventlog.EventHandled;
import com.loopers.domain.eventlog.EventHandledId;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.eventlog.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.interfaces.consumer.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * order-events(판매 확정) 한 건을 product_metrics.sales_count 로 멱등하게 반영한다.
 *
 * <p>멱등 원장 스코프 식별자(handler)를 catalog collector 와 <b>다르게</b> 둔다 — 같은 event_handled 테이블을
 * 공유하되 소비자별로 멱등 판정이 독립되도록(결정 ④의 복합 PK 설계). like/view collector 와 다른 스레드지만,
 * ProductMetrics 의 @DynamicUpdate 가 sales_count 컬럼만 UPDATE 해 교차 lost update 를 막는다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductSalesApplicationService {

    public static final String HANDLER = "product-sales";

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public void apply(OrderEventMessage message) {
        EventHandledId handledId = new EventHandledId(message.eventId(), HANDLER);
        if (eventHandledJpaRepository.existsById(handledId)) {
            return; // 이미 처리한 이벤트 — 중복 흡수
        }

        ProductMetrics metrics = productMetricsJpaRepository.findById(message.productId())
                .orElseGet(() -> ProductMetrics.of(message.productId()));

        switch (message.type()) {
            case PRODUCT_SOLD -> metrics.increaseSales(message.quantity());
        }

        productMetricsJpaRepository.save(metrics);
        eventHandledJpaRepository.save(EventHandled.of(message.eventId(), HANDLER));
    }
}
