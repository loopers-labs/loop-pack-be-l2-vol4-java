package com.loopers.domain.order;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 금액 계산 도메인 서비스.
 *
 * - 상태(Repository) 없이 순수한 금액 계산 로직만 담당
 * - 향후 할인·쿠폰·세금 등 가격 정책이 추가될 경우 이 서비스에서 중앙 관리한다.
 */
@Component
public class OrderPricingService {

    /**
     * 주문 항목 목록의 총 금액을 계산한다.
     * 각 항목의 단가 × 수량 합산.
     *
     * @param items 주문 항목 목록
     * @return 총 주문 금액
     */
    public int calculateTotal(List<OrderItemModel> items) {
        return items.stream()
            .mapToInt(OrderItemModel::totalPrice)
            .sum();
    }
}
