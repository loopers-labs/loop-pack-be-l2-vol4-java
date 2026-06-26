package com.loopers.domain.payment;

import java.util.List;

/**
 * 외부 PG 시스템에 대한 도메인 포트. 도메인은 PG를 이 추상으로만 알며,
 * 실제 HTTP 호출/타임아웃/예외 변환은 infrastructure 구현체가 담당한다.
 */
public interface PaymentGateway {

    Result requestPayment(Command command);

    /** transactionKey로 PG에 결제 진짜 상태를 조회한다 (콜백 검증·정합성 복구에 사용). */
    Result getTransaction(Long userId, String transactionKey);

    /** orderId로 PG의 결제건 목록을 조회한다 (키 미보유 미아 복구). 없으면 빈 목록. */
    List<Result> findTransactionsByOrderId(Long userId, Long orderId);

    record Command(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo,
        Long amount
    ) {}

    record Result(
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {}
}
