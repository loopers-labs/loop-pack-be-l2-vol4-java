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
    public OrderModel createOrder(
        Long userId,
        Long originalPrice,
        Long discountAmount,
        Long finalPrice,
        Long userCouponId,
        List<OrderItemCommand> itemCommands
    ) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        OrderModel order = orderRepository.save(
            new OrderModel(userId, originalPrice, discountAmount, finalPrice, userCouponId)
        );
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

    /**
     * 주문을 결제 완료(PAID)로 확정한다. 이미 PENDING이 아니면 멱등하게 무시한다.
     * (중복 콜백/조회 복구가 같은 주문을 여러 번 확정하려 해도 안전)
     */
    @Transactional
    public void confirm(Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));


        // PENDING이 아니면 멱등하게 무시한다.
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.confirm();
        orderRepository.save(order);
    }
}
