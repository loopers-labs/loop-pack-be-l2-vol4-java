package com.loopers.product.application.event;

import com.loopers.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * products.like_count 를 SSOT(likes)에서 주기적으로 재계산해 ++ 드리프트(이벤트 유실 등)를 교정한다.
 * 최초 backfill 과 동일한 쿼리이며, 호출 시점만 다르다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeCountReconciler {

    private final ProductRepository productRepository;

    @Scheduled(cron = "${product.like-count.reconcile-cron:0 0 * * * *}")
    @Transactional
    public void reconcile() {
        int updated = productRepository.reconcileLikeCounts();
        log.info("like_count reconcile 완료 updated={}", updated);
    }
}
