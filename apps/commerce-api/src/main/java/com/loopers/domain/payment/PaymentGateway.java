package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentGateway {

    /** 결제 요청 접수. 결과 타입으로 ACCEPTED/TIMEOUT/NOT_ATTEMPTED 를 구분해 반환. */
    PgRequestResult requestPayment(PgPaymentCommand command);

    /** transactionKey 로 거래 단건 상태 조회. PG 는 X-USER-ID 로 소유권을 필터링한다. */
    Optional<PgTransaction> getTransaction(Long userId, String transactionKey);

    /** 타임아웃 역조회용: orderId 로 PG 에 생성된 거래를 조회. */
    Optional<PgTransaction> findByOrderId(Long userId, Long orderId);
}