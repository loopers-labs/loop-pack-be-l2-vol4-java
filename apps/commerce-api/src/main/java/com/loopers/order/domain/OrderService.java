package com.loopers.order.domain;

import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OrderService {

    public OrderModel getOrThrow(Optional<OrderModel> order) {
        return order.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }

    public OrderModel createOrder(Long userId, List<ProductModel> products, Map<Long, Integer> quantities) {
        List<OrderItemModel> items = products.stream()
            .map(p -> {
                int qty = quantities.getOrDefault(p.getId(), 0);
                p.decreaseStock(qty);
                return new OrderItemModel(p.getId(), p.getName(), p.getPrice(), qty);
            })
            .toList();
        return new OrderModel(userId, items);
    }
}
