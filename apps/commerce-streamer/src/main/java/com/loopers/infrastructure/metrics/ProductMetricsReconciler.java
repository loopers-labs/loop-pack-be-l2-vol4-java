package com.loopers.infrastructure.metrics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * product_metrics 측정값 정합성 안전망. 비동기 집계(MetricsAggregator)는 이벤트 유실/재처리로 누적 오차가
 * 생길 수 있어, 이 스케줄러가 <b>진실원천</b>으로 카운터를 주기적으로 덮어써 오차를 수렴시킨다.
 *
 * <ul>
 *   <li>like_count  ← product_like 활성 행 수(COUNT)</li>
 *   <li>sales_count ← 결제 완료(PAID) 주문의 order_item 수량 합</li>
 * </ul>
 * view_count 는 별도 진실원천이 없어(원자 로그 미보존) 교정 대상에서 제외한다 — 이벤트 집계값을 그대로 둔다.
 *
 * <p>상관 서브쿼리 UPDATE 한 방이라 상품 수가 많으면 무겁다(풀 리컨실). 운영에서는 한산 시간대 또는
 * "최근 변경 상품만" 증분 리컨실로 좁히는 게 정석 — 현 단계는 최소 구현.
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsReconciler {

    private static final Logger log = LoggerFactory.getLogger(ProductMetricsReconciler.class);

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${product-metrics.reconcile-cron:0 */10 * * * *}")
    @Transactional
    public void reconcile() {
        int updated = jdbcTemplate.update("""
                UPDATE product_metrics pm
                   SET pm.like_count = (
                           SELECT COUNT(*) FROM product_like pl
                            WHERE pl.product_id = pm.product_id AND pl.deleted_at IS NULL
                       ),
                       pm.sales_count = (
                           SELECT COALESCE(SUM(oi.quantity), 0)
                             FROM order_item oi
                             JOIN orders o ON o.id = oi.order_id
                            WHERE oi.product_id = pm.product_id
                              AND oi.deleted_at IS NULL
                              AND o.status = 'PAID'
                       ),
                       pm.updated_at = now()
                """);
        log.info("product_metrics reconcile 완료(like/sales): rows={}", updated);
    }
}
