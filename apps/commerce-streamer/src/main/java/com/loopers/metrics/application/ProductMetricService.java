package com.loopers.metrics.application;

import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.CatalogEventMessage;
import com.loopers.metrics.interfaces.OrderPaidMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * product_metrics(이벤트로만 갱신하는 read model) 갱신. 이벤트가 담아 온 값(수량·delta)을 그대로 증분한다.
 * SSOT 를 다시 읽지 않으므로 producer 테이블과 결합하지 않는다. 재전달 중복은 배치 reconcile 이 교정한다.
 */
@Service
@RequiredArgsConstructor
public class ProductMetricService {

    private final ProductMetricJpaRepository productMetricJpaRepository;

    /** 결제완료 이벤트 → 담긴 라인의 수량만큼 판매량 증분. */
    @Transactional
    public void apply(OrderPaidMessage message) {
        message.items().forEach(line ->
                productMetricJpaRepository.increaseSales(line.productId(), line.quantity()));
    }

    /** 좋아요=delta(±1) 증분, 조회=delta(+1) 증분. */
    @Transactional
    public void applyCatalog(CatalogEventMessage message) {
        switch (message.type()) {
            case LIKE -> productMetricJpaRepository.increaseLike(message.productId(), message.delta());
            case VIEW -> productMetricJpaRepository.increaseView(message.productId(), message.delta());
        }
    }
}
