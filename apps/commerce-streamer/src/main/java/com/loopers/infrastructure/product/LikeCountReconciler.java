package com.loopers.infrastructure.product;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 수 정합성 안전망. 비동기 집계(LikeCountConsumer)는 이벤트 유실(AFTER_COMMIT send 실패)이나
 * 재처리 이중 반영으로 product.likes_count에 누적 오차가 생길 수 있다. 이 스케줄러가 <b>진실원천인
 * product_like 테이블의 활성 행 수</b>로 카운터를 주기적으로 덮어써 오차를 수렴시킨다.
 *
 * <p>상관 서브쿼리 UPDATE 한 방이라 상품 수가 많으면 무겁다(풀 리컨실). 운영에서는 트래픽 한산 시간대
 * 또는 "최근 변경된 상품만" 증분 리컨실로 좁히는 게 정석 — 현 단계는 최소 구현.
 */
@Component
@RequiredArgsConstructor
public class LikeCountReconciler {

    private static final Logger log = LoggerFactory.getLogger(LikeCountReconciler.class);

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${like-events.reconcile-cron:0 */10 * * * *}")
    @Transactional
    public void reconcile() {
        int updated = jdbcTemplate.update("""
                UPDATE product p
                   SET p.likes_count = (
                       SELECT COUNT(*) FROM product_like pl
                        WHERE pl.product_id = p.id AND pl.deleted_at IS NULL
                   )
                """);
        log.info("좋아요 수 reconcile 완료: rows={}", updated);
    }
}
