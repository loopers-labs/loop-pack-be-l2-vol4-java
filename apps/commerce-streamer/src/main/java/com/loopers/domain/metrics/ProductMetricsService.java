package com.loopers.domain.metrics;

import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderSnapshotItem;
import com.loopers.domain.order.OrderSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricSummaryRepository productMetricSummaryRepository;
    private final ProductMetricDailyRepository productMetricDailyRepository;
    private final OrderSnapshotRepository orderSnapshotRepository;

    public void recordView(String productId, LocalDate today) {
        productMetricSummaryRepository.incrementViewCount(productId);
        productMetricDailyRepository.incrementViewCount(productId, today);
    }

    public void recordLike(String productId, LocalDate today) {
        productMetricSummaryRepository.incrementLikeCount(productId);
        productMetricDailyRepository.incrementLikeDelta(productId, today);
    }

    public void recordLikeCancel(String productId, LocalDate today) {
        productMetricSummaryRepository.decrementLikeCount(productId);
        productMetricDailyRepository.decrementLikeDelta(productId, today);
    }

    public void recordPurchase(String eventId, String orderId, LocalDate today) {
        OrderSnapshot snapshot = orderSnapshotRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("주문 snapshot을 찾을 수 없습니다. orderId=" + orderId));

        for (OrderSnapshotItem item : snapshot.items()) {
            if (item.productId() == null || item.quantity() == null) {
                log.warn("상품 정보가 없는 주문 snapshot item 무시 [eventId={}, orderId={}]", eventId, orderId);
                continue;
            }
            productMetricSummaryRepository.incrementPurchaseCount(item.productId(), item.quantity());
            productMetricDailyRepository.incrementPurchaseQuantity(item.productId(), today, item.quantity());
        }
    }
}
