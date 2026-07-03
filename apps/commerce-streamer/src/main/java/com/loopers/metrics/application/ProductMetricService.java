package com.loopers.metrics.application;

import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.CatalogEventMessage;
import com.loopers.metrics.interfaces.OrderPaidMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * product_metrics(=SSOT 위의 MV) 갱신. 이벤트는 트리거이고, 값은 SSOT 에서 재계산해 덮어쓴다.
 * → 같은 이벤트가 두 번 와도 두 번 재계산 = 같은 값. dedup 테이블(event_handled) 없이 멱등.
 */
@Service
@RequiredArgsConstructor
public class ProductMetricService {

    private final ProductMetricJpaRepository productMetricJpaRepository;

    /** 주문 이벤트(트리거) → 담긴 상품들의 판매량을 order_items 에서 재계산. */
    @Transactional
    public void apply(OrderPaidMessage message) {
        message.items().stream()
                .map(OrderPaidMessage.Line::productId)
                .distinct()
                .forEach(productId ->
                        productMetricJpaRepository.setSales(productId, productMetricJpaRepository.sumOrderedQuantity(productId)));
    }

    /** 좋아요=likes 재계산 덮어쓰기(멱등), 조회=근사 증분. */
    @Transactional
    public void applyCatalog(CatalogEventMessage message) {
        switch (message.type()) {
            case LIKE -> productMetricJpaRepository.setLike(
                    message.productId(), productMetricJpaRepository.countLikes(message.productId()));
            case VIEW -> productMetricJpaRepository.increaseView(message.productId(), message.delta());
        }
    }
}
