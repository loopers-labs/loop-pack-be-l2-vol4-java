package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 도메인 서비스 — 순수 POJO. Repository/인프라에 의존하지 않고,
 * 로드된 도메인 객체(Product)들만 받아 여러 엔티티에 얽힌 규칙을 수행한다.
 * 규칙: 각 상품의 재고를 차감하고, 주문 시점 스냅샷으로 주문을 조립한다.
 */
@Component
public class OrderService {

    public OrderModel place(Long userId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderItemModel> items = new ArrayList<>();
        for (OrderLine line : lines) {
            ProductModel product = line.product();
            Quantity quantity = Quantity.of(line.quantity());
            product.decreaseStock(quantity);
            items.add(new OrderItemModel(
                product.getId(),
                product.getName(),
                Money.of(product.getPrice()),
                quantity
            ));
        }
        return new OrderModel(userId, items);
    }
}
