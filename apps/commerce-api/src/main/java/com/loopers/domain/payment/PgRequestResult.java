package com.loopers.domain.payment;

/**
 * PG 결제 요청의 접수 결과. 폴백 경로(타임아웃/서킷 차단)를 결과 타입으로 표현해
 * 어댑터가 예외를 던져 결제 자체를 죽이지 않도록 한다.
 */
public record PgRequestResult(Outcome outcome, String transactionKey) {

    public enum Outcome {
        ACCEPTED,
        TIMEOUT,       // read timeout — PG 에 거래가 생성됐을 수 있어 orderId 역조회 복구 대상.
        NOT_ATTEMPTED  // 서킷 Open 및 5xx  — PG 에 요청 자체가 안 갔으므로 재요청/거절 대상
    }

    public static PgRequestResult accepted(String transactionKey) {
        return new PgRequestResult(Outcome.ACCEPTED, transactionKey);
    }

    public static PgRequestResult timeout() {
        return new PgRequestResult(Outcome.TIMEOUT, null);
    }

    public static PgRequestResult notAttempted() {
        return new PgRequestResult(Outcome.NOT_ATTEMPTED, null);
    }
}
