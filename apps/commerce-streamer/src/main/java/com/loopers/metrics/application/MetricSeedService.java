package com.loopers.metrics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * product_metrics 에 일별 더미를 채운다 — 배치를 돌리려면 최소 7~30일 과거가 필요한데 기다릴 수 없기 때문이다.
 * 시드는 절대값 세팅이라 이벤트 누적(increase upsert)이 아니라 덮어쓰기 upsert 를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class MetricSeedService {

    private final JdbcTemplate jdbcTemplate;

    /** [from, to] 각 날짜 × 각 상품에 결정적 더미 카운트를 채운다. 이미 있으면 덮어쓴다. */
    @Transactional
    public int seed(LocalDate from, LocalDate to, List<Long> productIds) {
        int rows = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (Long productId : productIds) {
                MetricSeedGenerator.DayCount c = MetricSeedGenerator.generate(productId, date);
                jdbcTemplate.update("""
                        INSERT INTO product_metrics
                            (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW())
                        ON DUPLICATE KEY UPDATE
                            view_count = VALUES(view_count),
                            like_count = VALUES(like_count),
                            sales_count = VALUES(sales_count),
                            updated_at = NOW()
                        """, date, productId, c.view(), c.like(), c.sales());
                rows++;
            }
        }
        return rows;
    }
}
