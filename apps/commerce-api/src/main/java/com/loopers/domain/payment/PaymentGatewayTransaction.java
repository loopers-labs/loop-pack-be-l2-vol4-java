package com.loopers.domain.payment;

import java.util.List;

/**
 * PG 결제 트랜잭션의 확정 상태 단면. by-order 조회는 key 를 모르고 되묻기에 응답의 transactionKey 를 보존해야
 * 이후 결제에 흡수(assignTransactionKey)해 수렴시킬 수 있다.
 */
public record PaymentGatewayTransaction(
    String transactionKey,
    PaymentStatus status,
    String reason
) {

    private static final String NOT_ACKNOWLEDGED_REASON = "PG 미접수";

    /**
     * 한 주문에 대한 PG 트랜잭션 목록에서 수렴시킬 대표 결과 하나를 고른다.
     */
    public static PaymentGatewayTransaction resolveFrom(List<PaymentGatewayTransaction> transactions) {
        if (transactions.isEmpty()) {
            return new PaymentGatewayTransaction(null, PaymentStatus.FAILED, NOT_ACKNOWLEDGED_REASON);
        }
        return transactions.stream()
            .filter(transaction -> transaction.status() == PaymentStatus.SUCCESS)
            .findFirst()
            .orElse(transactions.getFirst());
    }
}
