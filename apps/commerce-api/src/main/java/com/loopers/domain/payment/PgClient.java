package com.loopers.domain.payment;

import java.util.Optional;

public interface PgClient {

    Optional<PgPaymentResult> requestPayment(PgPaymentCommand command);
}
