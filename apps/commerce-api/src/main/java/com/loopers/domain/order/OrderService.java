package com.loopers.domain.order;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 도메인 협력 Service (스타일 2 - Percival 정통).
 *
 * <p>Repository / 외부 시스템 의존 없이 호출자가 조회해서 넘긴 도메인 객체만으로 협력한다.
 * 모든 영속성 호출과 트랜잭션 경계는 {@link com.loopers.application.order.OrderApplicationService}가 책임진다.
 *
 * <p>한 줄 단위 문맥은 {@link OrderLine} 으로 묶어서 받는다. 여러 리스트(products/stocks/items)를
 * 인덱스로 매칭하던 약한 결합을 제거한다.
 */
@Service
public class OrderService {

    /**
     * 주문 생성 + 항목별 재고 차감 도메인 협력.
     *
     * <p>각 {@link OrderLine} 의 Stock 에 대해 {@link com.loopers.domain.stock.StockModel#deduct(int)}
     * 도메인 메서드를 호출하여 재고 음수 방지 규칙을 위임한다.
     */
    public OrderModel createWithStockDeduction(Long userId, List<OrderLine> orderLines) {
        OrderModel order = new OrderModel(userId);
        for (OrderLine line : orderLines) {
            line.stock().deduct(line.quantity());
            order.addItem(new OrderItemModel(
                order,
                line.product().getId(),
                line.product().getName(),
                line.product().getPrice(),
                line.quantity()
            ));
        }
        order.confirmAmounts();
        return order;
    }
}
