package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Order 도메인 서비스.
 *
 * - 상태(Repository) 없이 도메인 객체 간 협력 로직만 담당
 * - OrderFacade에서 영속화 전·후 순수 도메인 로직을 위임받아 처리한다.
 *   · 주문 엔티티 조립: Product 스냅샷 → OrderItem, 총 금액 계산은 OrderPricingService에 위임
 */
@RequiredArgsConstructor
@Component
public class OrderDomainService {

    private final OrderPricingService orderPricingService;

    /**
     * 주문 엔티티 조립.
     * Product 정보를 스냅샷으로 OrderItem에 담고, 총 금액 계산을 OrderPricingService에 위임한다.
     * 영속화(save)는 Facade에서 수행한다.
     *
     * @param userId      주문자 ID
     * @param products    조회된 ProductModel 목록 (활성 상품만)
     * @param quantityMap productId → 주문 수량
     * @return 항목·금액이 확정된 OrderModel (미저장 상태)
     */
    public OrderModel buildOrder(Long userId, List<ProductModel> products, Map<Long, Integer> quantityMap) {
        OrderModel order = new OrderModel(userId);
        products.forEach(product -> {
            int quantity = quantityMap.get(product.getId());
            OrderItemModel item = new OrderItemModel(
                order,
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getBrand().getName(),
                quantity
            );
            order.addItem(item);
        });
        order.applyPricing(orderPricingService.calculateTotal(order.getItems()), 0);
        return order;
    }
}
