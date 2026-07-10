package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// event_handled 선점(멱등 판정)과 product_metrics 갱신을 하나의 트랜잭션으로 묶는다.
// 선점 실패(이미 처리된 이벤트) 시 즉시 반환해, 컨슈머가 재전달을 받아도 지표가 중복 반영되지 않는다.
@RequiredArgsConstructor
@Component
public class MetricsFacade {

    private final EventHandledService eventHandledService;
    private final ProductMetricsService productMetricsService;

    public record SalesItem(Long productId, Long quantity) {
    }

    @Transactional
    public void applyLike(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.increaseLikeCount(productId);
    }

    @Transactional
    public void applyUnlike(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.decreaseLikeCount(productId);
    }

    @Transactional
    public void applyView(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.increaseViewCount(productId);
    }

    // 주문 하나에 아이템이 여럿이어도 이벤트(주문) 단위로 한 번만 markHandled를 호출한 뒤 아이템별로 반영한다.
    @Transactional
    public void applySales(String eventId, List<SalesItem> items) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        items.forEach(item -> productMetricsService.increaseSalesCount(item.productId(), item.quantity()));
    }
}
