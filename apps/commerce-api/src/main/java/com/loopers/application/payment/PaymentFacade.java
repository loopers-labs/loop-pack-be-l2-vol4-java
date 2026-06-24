package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
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

    public PaymentInfo requestPayment(String loginId, String loginPw, Long orderId, CardType cardType, String cardNo) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        OrderModel order = orderService.getByIdAndValidateOwner(orderId, user.getId());

        PaymentModel payment = paymentService.pay(
                user.getUserNumber(), order.getId(), order.getOrderNumber(), cardType, cardNo, order.getFinalPrice()
        );

        return PaymentInfo.from(payment);
    }
}
