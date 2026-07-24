package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.ProductMetricsDailyService;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// event_handled 선점(멱등 판정)과 product_metrics(+ product_metrics_daily) 갱신을 하나의 트랜잭션으로 묶는다.
// 선점 실패(이미 처리된 이벤트) 시 즉시 반환해, 컨슈머가 재전달을 받아도 지표가 중복 반영되지 않는다.
// 누적(product_metrics)과 일별(product_metrics_daily)은 같은 이벤트에서 파생되는 동일 계열 프로젝션이라
// 하나의 트랜잭션·하나의 event_handled 가드로 원자적으로 함께 갱신한다(부분 반영 방지).
// metric_date는 애플리케이션 기준 KST(TimeZone=Asia/Seoul) 처리 시각의 날짜다 - 이벤트당 한 번 계산해 공유한다.
@RequiredArgsConstructor
@Component
public class MetricsFacade {

    private final EventHandledService eventHandledService;
    private final ProductMetricsService productMetricsService;
    private final ProductMetricsDailyService productMetricsDailyService;

    public record OrderItem(Long productId, Long quantity) {
    }

    @Transactional
    public void applyLike(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.increaseLikeCount(productId);
        productMetricsDailyService.increaseLikeCount(productId, LocalDate.now());
    }

    @Transactional
    public void applyUnlike(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.decreaseLikeCount(productId);
        productMetricsDailyService.decreaseLikeCount(productId, LocalDate.now());
    }

    @Transactional
    public void applyView(String eventId, Long productId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        productMetricsService.increaseViewCount(productId);
        productMetricsDailyService.increaseViewCount(productId, LocalDate.now());
    }

    // 주문 하나에 아이템이 여럿이어도 이벤트(주문) 단위로 한 번만 markHandled를 호출한 뒤 아이템별로 반영한다.
    @Transactional
    public void applyOrder(String eventId, List<OrderItem> items) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        LocalDate metricDate = LocalDate.now();
        items.forEach(item -> {
            productMetricsService.increaseOrderCount(item.productId(), item.quantity());
            productMetricsDailyService.increaseOrderCount(item.productId(), metricDate, item.quantity());
        });
    }
}
