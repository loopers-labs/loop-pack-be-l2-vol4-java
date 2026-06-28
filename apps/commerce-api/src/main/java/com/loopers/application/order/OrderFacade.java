package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands) {
        List<ProductService.DeductStockCommand> stockCommands = commands.stream()
            .map(c -> new ProductService.DeductStockCommand(c.productId(), c.quantity()))
            .toList();

        List<ProductSnapshot> snapshots = productService.deductStocks(stockCommands);
        return OrderInfo.from(orderService.createOrder(userId, snapshots));
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getOrders(Long userId, String startAt, String endAt) {
        return orderService.getOrders(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderService.getAllOrders(pageable).map(OrderInfo::from);
    }

    public record OrderItemCommand(Long productId, int quantity) {}
}
