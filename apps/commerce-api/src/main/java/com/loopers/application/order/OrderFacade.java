package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.quantity.Quantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;

    @Transactional
    public OrderInfo place(Long userId, List<OrderLineCommand> lines) {
        List<OrderItem> items = lines.stream()
            .map(line -> {
                Product product = productService.getProduct(line.productId());
                Quantity quantity = new Quantity(line.quantity());
                product.decreaseStock(quantity);
                return new OrderItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    quantity
                );
            })
            .toList();
        Order order = orderService.place(userId, items);
        return OrderInfo.from(order, items);
    }
}
