package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    /**
     * 주문 생성 + 결제.
     *
     * OrderService 트랜잭션이 종료된 뒤 결과를 확인한다.
     * - CANCELLED 상태이면 결제 실패로 간주하고 400 BAD_REQUEST.
     *   (트랜잭션 안에서 throw하면 보상 작업이 함께 롤백되므로, 트랜잭션 경계 바깥에서 처리한다.)
     */
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items) {
        OrderModel order = orderService.createOrder(userId, items);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            String reason = paymentRepository.findByOrderId(order.getId())
                .map(PaymentModel::getFailureReason)
                .orElse("결제 실패로 주문이 취소되었습니다.");
            throw new CoreException(ErrorType.BAD_REQUEST, "PAYMENT_FAILED: " + reason);
        }

        return OrderInfo.from(order);
    }

    public List<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderService.getOrders(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderService.getOrder(userId, orderId);
        return OrderInfo.from(order);
    }

    public List<OrderInfo> getAllOrders(int page, int size) {
        return orderService.getAllOrders(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    public OrderInfo getOrderAdmin(Long orderId) {
        OrderModel order = orderService.findById(orderId);
        return OrderInfo.from(order);
    }
}
