package com.loopers.application.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.application.payment.payment.PaymentCommandService;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.application.event.order.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderFacade {

    private final OrderCommandService orderCommandService;
    private final PaymentCommandService paymentCommandService;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public OrderResult.Detail placeOrder(OrderCommand.Create command) {
        Order order = orderCommandService.createPendingOrder(command);
        if (!order.requiresPayment()) {
            Order paidOrder = orderCommandService.markPaid(order.getId());
            orderEventPublisher.publishOrderPaid(paidOrder);
            return OrderResult.Detail.from(paidOrder, null);
        }

        Payment payment = paymentCommandService.createRequestedPayment(order.getId(), order.getFinalAmount());
        return OrderResult.Detail.from(order, payment);
    }
}
