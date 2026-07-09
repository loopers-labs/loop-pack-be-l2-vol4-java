package com.loopers.application.product;

import com.loopers.domain.product.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세 조회 시 조회 이벤트를 <b>독립 트랜잭션</b>으로 기록한다(BEFORE_COMMIT 리스너가 outbox로 적재).
 * 조회는 상태 변경이 없는 읽기 경로라, 조회수 신호만을 위한 최소 트랜잭션을 별도로 연다.
 * (상세 응답 자체는 캐시에서 오므로 이 기록이 응답 데이터를 바꾸지 않는다.)
 */
@Component
@RequiredArgsConstructor
public class ProductViewRecorder {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void record(Long productId) {
        eventPublisher.publishEvent(ProductViewedEvent.of(productId));
    }
}
