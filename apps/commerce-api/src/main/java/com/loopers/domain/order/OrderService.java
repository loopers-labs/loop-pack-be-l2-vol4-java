package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 도메인 서비스.
 * 이미 조회된 상품들을 받아 재고 차감(Product의 책임)과 주문 생성(Order의 책임)을 협력시킨다.
 * 상태를 갖지 않으며, 저장(영속성)은 Application Layer가 담당한다.
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
                product.decreaseStock(quantity); // 재고 부족 시 도메인에서 예외
                return OrderItem.of(product.getId(), product.getName(), product.getPrice(), Quantity.of(quantity));
            })
            .toList();

        return Order.create(userId, items);
    }
}
