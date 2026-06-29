package com.loopers.domain.payment;

/**
 * PG 결제 요청의 <strong>결과를 알 수 없을 때</strong>(미확정) 던지는 예외.
 *
 * <p>타임아웃·연결 오류는 <strong>요청은 나갔으나 응답을 못 받은</strong> 경우라 트랜잭션 생성
 * 여부가 불확실하다 — 요청이 PG 에 도달해 트랜잭션이 만들어졌을 수도, 아닐 수도 있다.
 * (서킷 open 은 요청이 아예 안 나가 미생성이 확정되므로 여기 해당하지 않는다 —
 * {@link PgRequestRejectedException} 로 처리한다.)
 * 따라서 절대 즉시 FAILED 로 단정하면 안 되며, 결제는 REQUESTED 로,
 * 주문은 PAYMENT_IN_PROGRESS 로 남겨두고 콜백 수신 또는 대사 스케줄러의 PG 조회로 보정한다.
 *
 * @see PgRequestRejectedException 트랜잭션 미생성이 확정된 경우
 */
public class PgIndeterminateException extends RuntimeException {

    public PgIndeterminateException(String message, Throwable cause) {
        super(message, cause);
    }
}
