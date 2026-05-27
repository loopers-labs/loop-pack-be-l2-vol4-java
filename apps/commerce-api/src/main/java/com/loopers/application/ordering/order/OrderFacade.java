package com.loopers.application.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.application.payment.payment.PaymentCommandService;
import com.loopers.domain.payment.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderFacade {

    private final OrderCommandService orderCommandService;
    private final PaymentCommandService paymentCommandService;

    @Transactional
    public OrderResult.Detail placeOrder(OrderCommand.Create command) {
        Order order = orderCommandService.createPendingOrder(command);
        Payment payment = paymentCommandService.createRequestedPayment(order.getId(), order.getTotalAmount());
        return OrderResult.Detail.from(order, payment);
    }
}
