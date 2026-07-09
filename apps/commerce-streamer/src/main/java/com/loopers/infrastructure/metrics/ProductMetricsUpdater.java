package com.loopers.infrastructure.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * product_metrics 의 <b>측정값 컬럼</b>(like_count/sales_count/view_count)을 상품별 합산 델타로 batch UPDATE 한다.
 * commerce-streamer 는 commerce-api 의 JPA 엔티티를 공유하지 않으므로(앱 경계) 테이블에 직접 쓴다.
 *
 * <p>측정값은 streamer 소유 컬럼이고 차원(brand_id/price/deleted_at)은 commerce-api 소유라, 여기서는
 * 오직 측정값 컬럼만 targeted UPDATE 한다 → 두 writer 가 서로의 컬럼을 덮어쓰지 않는다(2-writer 분리).
 * 행 자체는 commerce-api 가 상품 생성 시 만들므로 여기서는 UPDATE 만 한다(행 없으면 no-op, reconcile 이 교정).
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsUpdater {

    private final JdbcTemplate jdbcTemplate;

    /** {@code like_count = GREATEST(0, like_count + Δ)} — 비동기 누적분이 음수로 떨어지는 것을 가드. */
    public void applyLikeDeltas(Map<Long, Long> deltaByProduct) {
        batchUpdate(
                "UPDATE product_metrics SET like_count = GREATEST(0, like_count + ?), updated_at = now() WHERE product_id = ?",
                deltaByProduct);
    }

    /** 조회수 += Δ (항상 양수 누적). */
    public void applyViewDeltas(Map<Long, Long> deltaByProduct) {
        batchUpdate(
                "UPDATE product_metrics SET view_count = view_count + ?, updated_at = now() WHERE product_id = ?",
                deltaByProduct);
    }

    /** 판매량 += Δ (주문 결제 완료 수량). */
    public void applySalesDeltas(Map<Long, Long> deltaByProduct) {
        batchUpdate(
                "UPDATE product_metrics SET sales_count = sales_count + ?, updated_at = now() WHERE product_id = ?",
                deltaByProduct);
    }

    private void batchUpdate(String sql, Map<Long, Long> deltaByProduct) {
        List<Object[]> batchArgs = new ArrayList<>(deltaByProduct.size());
        for (Map.Entry<Long, Long> entry : deltaByProduct.entrySet()) {
            if (entry.getValue() == null || entry.getValue() == 0) {
                continue; // 상쇄되어 0이 된 델타는 UPDATE 불필요
            }
            batchArgs.add(new Object[]{entry.getValue(), entry.getKey()});
        }
        if (batchArgs.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
