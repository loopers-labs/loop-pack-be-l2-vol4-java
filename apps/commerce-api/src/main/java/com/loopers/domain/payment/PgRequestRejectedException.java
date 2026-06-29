package com.loopers.domain.payment;

/**
 * PG 결제 요청이 <strong>확정적으로 거부</strong>됐을 때 던지는 예외.
 *
 * <p>"트랜잭션 미생성"이 확정되는 두 경우에 던진다:
 * <ul>
 *   <li><strong>500 거부 소진</strong> — pg-simulator 의 요청 거부(HTTP 500)는 트랜잭션 생성
 *       <em>이전</em>에 발생하므로 미생성이 보장된다. 즉시 재시도(최대 N회)까지 모두 500 이면
 *       "결제가 시작되지 않았음"이 확정된다.</li>
 *   <li><strong>서킷 open</strong> — CircuitBreaker 가 호출을 실행하기 전에 차단하므로 요청이
 *       PG 로 나가지 않는다 → 트랜잭션이 만들어졌을 가능성이 0 이다.</li>
 * </ul>
 *
 * <p>두 경우 모두 호출자는 결제를 FAILED 처리하고 점유 자원을 즉시 원복할 수 있다.
 *
 * @see PgIndeterminateException 결과를 알 수 없는(미확정) 경우
 */
public class PgRequestRejectedException extends RuntimeException {

    public PgRequestRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
