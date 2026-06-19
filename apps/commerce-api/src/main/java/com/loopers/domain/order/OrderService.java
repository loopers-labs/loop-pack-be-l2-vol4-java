package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 도메인 서비스. 조회된 상품들의 재고 차감과 주문 생성을 협력시킨다.
 */
@Component
public class OrderService {

    public Order createOrder(Long userId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderItem> items = lines.stream()
            .map(line -> {
                Product product = line.product();
                int quantity = line.quantity();
                product.decreaseStock(quantity);
                return OrderItem.of(product.getId(), product.getName(), product.getPrice(), Quantity.of(quantity));
            })
            .toList();

        return Order.create(userId, items);
    }
}
