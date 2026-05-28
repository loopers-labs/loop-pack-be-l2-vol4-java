package com.loopers.application.order;

import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderProductProcessService;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;
    private final OrderProductProcessService orderProductProcessService;

    @Transactional
    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands) {
        List<Product> products = productService.findProductsByIds(productIds(commands));
        OrderResult result = orderProductProcessService.createOrder(userLoginId, commands, products);
        productService.saveProducts(products);
        return OrderInfo.from(orderService.saveOrder(result));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        return orderService.getOrders(userLoginId, startAt, endAt, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String userLoginId, Long orderId) {
        return OrderInfo.from(orderService.getOrder(userLoginId, orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(Integer page, Integer size) {
        return orderService.getAllOrders(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    private List<Long> productIds(List<OrderProductCommand> commands) {
        return commands.stream()
            .map(OrderProductCommand::productId)
            .distinct()
            .toList();
    }
}
