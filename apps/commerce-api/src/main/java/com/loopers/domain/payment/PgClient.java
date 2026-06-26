package com.loopers.domain.payment;

import java.util.List;

/**
 * 외부 PG(pg-simulator) 연동 포트. 구현체는 infrastructure.payment.PgSimulatorClient.
 * <p>
 * 모든 호출은 DB 트랜잭션 <b>밖</b>에서 이루어져야 한다 — 외부 HTTP 지연이 DB 커넥션/락을 잡지 않게 한다.
 * 어댑터는 40% 일시 실패(500)에 대해 재시도하고, 연속 실패 시 서킷을 열어 빠르게 차단한다.
 */
public interface PgClient {

    /**
     * 결제 요청. pg-simulator는 즉시 PENDING 거래를 발급하고(transactionKey), 실제 승인/거절은
     * 1~5초 뒤 비동기로 처리해 callbackUrl로 통지한다. 따라서 반환 status는 보통 PENDING이다.
     */
    PgTransaction requestPayment(PgPaymentRequest request);

    /**
     * 주문에 엮인 PG 거래 목록 조회 (reconcile 진실원천). 콜백이 유실돼 우리 쪽이 PENDING으로
     * 남았을 때, PG의 최종 상태를 직접 확인하는 용도.
     */
    List<PgTransaction> findTransactionsByOrder(Long orderId);
}
