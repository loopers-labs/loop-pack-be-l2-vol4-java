package com.loopers.application.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 상품 조회 로깅 리스너.
 *
 * <p>조회는 커밋 개념이 없는 읽기라 평범한 {@link EventListener}로 즉시(동기) 로깅한다.
 * 로그 출력만 하므로 트랜잭션이 필요 없다.
 */
@Slf4j
@Component
public class ViewLogListener {

    @EventListener
    public void onProductViewed(ProductViewedEvent event) {
        log.info("[UserAction] userId={}, action=VIEW, productId={}",
            event.userId(), event.productId());
    }

    /** 목록 페이지 조회 — 페이지 단위 탐색 행동 로그 (상품별 view_count 집계와 무관). */
    @EventListener
    public void onProductListViewed(ProductListViewedEvent event) {
        log.info("[UserAction] action=LIST_VIEW, brandId={}, sort={}, page={}",
            event.brandId(), event.sort(), event.page());
    }
}
