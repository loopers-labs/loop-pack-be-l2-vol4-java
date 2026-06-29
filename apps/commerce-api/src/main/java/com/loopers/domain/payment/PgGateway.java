package com.loopers.domain.payment;

import java.util.List;

/**
 * 비동기 PG(결제 대행사) 통신 추상화 인터페이스.
 *
 * <p>pg-simulator 처럼 즉시 transactionKey(PENDING) 를 반환하고,
 * 실제 결과는 callbackUrl 로 비동기 통보하는 방식의 PG 를 대상으로 한다.
 *
 * <p>{@link PaymentGateway} 가 Toss 스타일(인증→승인 2단계 동기)이라면,
 * 이 인터페이스는 비동기 콜백 방식의 PG 를 추상화한다.
 */
public interface PgGateway {

    /**
     * 결제 요청 전송.
     *
     * @return transactionKey — PG 가 발급한 거래 식별자. 콜백으로 최종 결과가 도착할 때 같은 키가 온다.
     * @throws PgRequestRejectedException 트랜잭션 미생성이 확정된 경우 — 500 거부 재시도 소진, 또는 서킷 open(요청 미전송)
     * @throws PgIndeterminateException 타임아웃·연결오류 등 요청은 나갔으나 트랜잭션 생성 여부가 불확실한 경우
     */
    String requestPayment(String userId, Long orderId, CardType cardType, String cardNo, Long amount, String callbackUrl);

    /**
     * orderId 로 해당 주문의 트랜잭션 목록 조회.
     *
     * <p>조회 실패(404 포함) 시 빈 리스트를 반환한다.
     */
    List<PgTransactionResult> findTransactionsByOrderId(String userId, String orderId);

    record PgTransactionResult(String transactionKey, String status, String reason) {}
}
