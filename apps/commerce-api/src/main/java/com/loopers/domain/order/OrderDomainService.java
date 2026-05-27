package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Order 도메인 서비스.
 *
 * - 상태(Repository) 없이 도메인 객체 간 협력 로직만 담당
 * - OrderFacade에서 영속화 전·후 순수 도메인 로직을 위임받아 처리한다.
 *   · 재고 사전 검증: 한 건이라도 부족하면 전체 실패 (주문은 원자적)
 *   · 주문 엔티티 조립: Product 스냅샷 → OrderItem, 합계 자동 누적
 */
@Component
public class OrderDomainService {

    /**
     * 재고 사전 검증.
     * 요청 수량이 현재 재고보다 많은 항목이 하나라도 있으면 BAD_REQUEST.
     *
     * @param stockMap   productId → StockModel
     * @param quantityMap productId → 요청 수량
     */
    public void validateStocks(Map<Long, StockModel> stockMap, Map<Long, Integer> quantityMap) {
        quantityMap.forEach((productId, requestedQty) -> {
            StockModel stock = stockMap.get(productId);
            if (stock.getQuantity() < requestedQty) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "재고가 부족합니다. productId=" + productId
                        + " (요청: " + requestedQty + ", 재고: " + stock.getQuantity() + ")");
            }
        });
    }

    /**
     * 주문 엔티티 조립.
     * Product 정보를 스냅샷으로 OrderItem에 담아 OrderModel을 구성한다.
     * 영속화(save)는 Facade에서 수행한다.
     *
     * @param userId      주문자 ID
     * @param products    조회된 ProductModel 목록 (활성 상품만)
     * @param quantityMap productId → 주문 수량
     * @return 항목이 추가된 OrderModel (미저장 상태)
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
        return order;
    }
}
