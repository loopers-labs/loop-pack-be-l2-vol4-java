package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Transactional
    public Order place(Long userId, List<OrderLine> lines) {
        List<OrderItem> items = lines.stream()
            .map(line -> {
                Product product = productService.getProduct(line.productId());
                product.decreaseStock(line.quantity());
                return new OrderItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    line.quantity()
                );
            })
            .toList();
        Order order = Order.place(userId, items);
        return orderRepository.save(order);
    }
}
