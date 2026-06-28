package com.loopers.domain.order;

import com.loopers.domain.product.ProductSnapshot;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Long userId, List<ProductSnapshot> snapshots) {
        List<OrderItem> items = snapshots.stream()
            .map(s -> new OrderItem(s.productId(), s.productName(), s.productPrice(), s.quantity()))
            .toList();

        long totalAmount = snapshots.stream()
            .mapToLong(s -> s.productPrice() * s.quantity())
            .sum();

        return orderRepository.save(new Order(userId, totalAmount, items));
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

    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
