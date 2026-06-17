package com.loopers.infrastructure.product;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 상품별 합산 좋아요 델타를 product.likes_count에 batch UPDATE로 반영한다.
 * commerce-streamer는 commerce-api의 JPA 엔티티를 공유하지 않으므로(앱 경계) 테이블에 직접 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ProductLikesUpdater {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@code likes_count = GREATEST(0, likes_count + Δ)} — 비동기 누적분이 음수로 떨어지는 일을 가드한다.
     * (정확한 절대값은 reconcile 스케줄러가 product_like COUNT로 최종 교정)
     */
    @Transactional
    public void applyDeltas(Map<Long, Long> deltaByProduct) {
        List<Object[]> batchArgs = new ArrayList<>(deltaByProduct.size());
        for (Map.Entry<Long, Long> entry : deltaByProduct.entrySet()) {
            if (entry.getValue() == 0) {
                continue; // +1과 -1이 상쇄된 상품은 UPDATE 불필요
            }
            batchArgs.add(new Object[]{entry.getValue(), entry.getKey()});
        }
        if (batchArgs.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "UPDATE product SET likes_count = GREATEST(0, likes_count + ?) WHERE id = ?",
                batchArgs);
    }
}
