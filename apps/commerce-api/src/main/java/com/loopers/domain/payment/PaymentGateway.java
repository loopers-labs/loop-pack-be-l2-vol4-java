package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentGateway {

    GatewayResult requestPayment(GatewayCommand command);

    /** PG가 응답하지 않으면 empty. */
    Optional<GatewayStatus> queryStatus(String transactionKey, Long userId);

    GatewayLookup queryByOrderId(Long orderId, Long userId);
}
