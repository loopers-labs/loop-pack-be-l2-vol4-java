package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.outbox.OutboxService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;
    private final OutboxService outboxService;

    @Transactional
    public OrderInfo.Create createOrder(OrderCommand.Create command) {
        // 데드락 방지: 여러 상품을 동시에 주문할 때 락 획득 순서를 productId 오름차순으로 고정
        List<OrderCommand.Create.Item> sortedItems = command.items().stream()
            .sorted(Comparator.comparing(OrderCommand.Create.Item::productId))
            .toList();

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderCommand.Create.Item item : sortedItems) {
            Product product = productService.getProduct(item.productId());
            productService.deductStock(item.productId(), item.quantity());
            items.add(new OrderItem(product.getId(), product.getName(), product.getPrice(), item.quantity()));
            totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }

        Order order = orderService.createOrder(command.userId(), totalPrice, items);
        outboxService.publishOrderCreatedEvent(order);
        return OrderInfo.Create.from(order);
    }
}
