package com.loopers.domain.payment;

/**
 * PG 연결 자체가 실패했을 때(=요청이 PG 에 도달하지 못한 게 확실할 때) 던지는 예외.
 *
 *  - 예: TCP ConnectException, DNS 실패 등 → 결제가 시작되지 않은 게 확실하므로 재시도 안전.
 *  - PG-Simulator 가 멱등키를 받지 않기 때문에, retry 가 안전한 케이스는 "요청 도달 전 실패" 로 한정해야 한다.
 *  - ReadTimeout / 5xx 는 결제 시작 여부 불확실 → PgUnknownException 로 분류 (UNKNOWN 후 폴링).
 *
 * Resilience4j retry-exceptions 에 이 클래스를 등록해 retry 트리거로 사용한다.
 */
public class PgConnectFailedException extends RuntimeException {

    public PgConnectFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
