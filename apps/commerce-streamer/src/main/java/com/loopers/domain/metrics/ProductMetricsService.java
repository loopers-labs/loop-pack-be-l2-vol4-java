package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이벤트를 상품 집계로 반영한다. 각 메서드는 한 트랜잭션에서 event_handled(멱등) 판단과 집계 반영을 함께 커밋한다.
 * 이미 처리한 eventId 는 skip 하므로 같은 이벤트가 재전달(At Least Once)돼도 결과는 한 번만 반영된다.
 */
@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    public static final String TYPE_LIKE_CHANGED = "LIKE_CHANGED";
    public static final String TYPE_PRODUCT_VIEWED = "PRODUCT_VIEWED";
    public static final String TYPE_ORDER_PAID = "ORDER_PAID";

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void applyLike(String eventId, Long productId, long delta) {
        if (alreadyHandled(eventId, TYPE_LIKE_CHANGED)) {
            return;
        }
        ProductMetricsModel metrics = productMetricsRepository.find(productId)
                .orElseGet(() -> ProductMetricsModel.of(productId));
        metrics.addLike(delta);
        productMetricsRepository.save(metrics);
    }

    @Transactional
    public void applyView(String eventId, Long productId) {
        if (alreadyHandled(eventId, TYPE_PRODUCT_VIEWED)) {
            return;
        }
        ProductMetricsModel metrics = productMetricsRepository.find(productId)
                .orElseGet(() -> ProductMetricsModel.of(productId));
        metrics.addView();
        productMetricsRepository.save(metrics);
    }

    @Transactional
    public void applyOrderPaid(String eventId, List<OrderItem> items) {
        if (alreadyHandled(eventId, TYPE_ORDER_PAID)) {
            return;
        }
        for (OrderItem item : items) {
            ProductMetricsModel metrics = productMetricsRepository.find(item.productId())
                    .orElseGet(() -> ProductMetricsModel.of(item.productId()));
            metrics.addSales(item.quantity());
            productMetricsRepository.save(metrics);
        }
    }

    /** 이미 처리한 이벤트면 true, 처음 보는 이벤트면 handled 로 기록하고 false 를 반환한다(같은 트랜잭션). */
    private boolean alreadyHandled(String eventId, String eventType) {
        if (eventHandledRepository.exists(eventId)) {
            return true;
        }
        eventHandledRepository.save(EventHandledModel.of(eventId, eventType));
        return false;
    }

    public record OrderItem(Long productId, long quantity) {}
}