package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public record OrderItemCommand(Long productId, int quantity) {}

    @Transactional
    public Order createOrder(Long userId, List<OrderItemCommand> commands) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));

        List<OrderItem> items = commands.stream().map(cmd -> {
            ProductModel product = productRepository.findById(cmd.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                    "[id = " + cmd.productId() + "] 상품을 찾을 수 없습니다."));
            product.decreaseStock(cmd.quantity());
            productRepository.save(product);
            return new OrderItem(product.getId(), product.getName(), product.getPrice(), cmd.quantity());
        }).toList();

        long totalPrice = items.stream()
            .mapToLong(item -> item.getProductPrice() * item.getQuantity())
            .sum();

        return orderRepository.save(new Order(userId, totalPrice, items));
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(Long userId, String startAt, String endAt) {
        return orderRepository.findByUserId(userId, startAt, endAt);
    }
}
