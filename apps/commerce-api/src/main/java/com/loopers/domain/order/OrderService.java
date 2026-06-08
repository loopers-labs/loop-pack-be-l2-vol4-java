package com.loopers.domain.order;

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

    @Transactional
    public OrderModel createOrder(Long userId, Long totalPrice, List<OrderItemCommand> itemCommands) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        OrderModel order = orderRepository.save(new OrderModel(userId, totalPrice));
        for (OrderItemCommand cmd : itemCommands) {
            orderRepository.saveItem(new OrderItemModel(
                order.getId(), cmd.productId(), cmd.productName(), cmd.unitPrice(), cmd.quantity()
            ));
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long id) {
        return orderRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getOrderItems(Long orderId) {
        return orderRepository.findItemsByOrderId(orderId);
    }
}
