package com.loopers.domain.payment;

/**
 * 외부 PG 연동 포트. 도메인은 이 인터페이스로만 PG 를 알고, 구체 구현은 infrastructure 가 역전 구현한다.
 */
public interface PaymentGateway {

    /**
     * PG 에 결제를 접수 요청하고 transactionKey 를 받는다. (동기 응답 = 접수증, 최종 결과 아님)
     */
    PaymentGatewayResult requestPayment(PaymentGatewayCommand command);

    /**
     * transactionKey 로 PG 에 결제의 확정 상태를 직접 조회한다.
     */
    PaymentGatewayTransaction getTransaction(Long userId, String transactionKey);
}
