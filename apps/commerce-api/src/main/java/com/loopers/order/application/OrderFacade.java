package com.loopers.order.application;

import com.loopers.order.domain.OrderItemModel;
import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands) {
        List<OrderItemModel> items = commands.stream()
            .map(command -> {
                ProductModel product = productService.getOrThrow(productRepository.find(command.productId()));
                product.decreaseStock(command.quantity());
                productRepository.save(product);
                return new OrderItemModel(product.getId(), product.getName(), product.getPrice(), command.quantity());
            })
            .toList();

        OrderModel order = new OrderModel(userId, items);
        return OrderInfo.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderService.getOrThrow(orderRepository.find(orderId));
        // [fix] 타인의 주문 조회 시 403 처리 누락
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        return OrderInfo.from(order);
    }
}
