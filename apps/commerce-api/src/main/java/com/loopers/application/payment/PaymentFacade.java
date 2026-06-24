package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PgClient pgClient;

    public PaymentInfo requestPayment(String loginId, String loginPw, Long orderId, CardType cardType, String cardNo) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        OrderModel order = orderService.getById(orderId);
        order.validateOwner(user.getId());

        PaymentModel payment = paymentService.create(order.getId(), cardType, cardNo, order.getFinalPrice());

        PgPaymentCommand command = new PgPaymentCommand(user.getId(), order.getId(), cardType, cardNo, order.getFinalPrice());
        pgClient.requestPayment(command)
                .ifPresent(result -> paymentService.applyPgResult(payment, result.transactionKey(), result.status()));

        return PaymentInfo.from(payment);
    }
}
