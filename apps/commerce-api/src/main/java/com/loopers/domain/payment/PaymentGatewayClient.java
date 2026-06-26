package com.loopers.domain.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.enums.CardType;
import com.loopers.domain.payment.enums.PgTransactionStatus;

public interface PaymentGatewayClient {

    Result request(Command command);

    QueryResult query(String transactionKey, Long userId);

    record Command(
            Long userId,
            String orderNumber,
            Long amount,
            CardType cardType,
            String cardNo
    ) {
        public static Command of(Long userId, OrderModel order, CardType cardType, String cardNo) {
            return new Command(userId, order.getOrderNumber(), order.getTotalMoney().getValue(), cardType, cardNo);
        }
    }

    record Result(String transactionKey) {}

    record QueryResult(PgTransactionStatus status) {}
}
