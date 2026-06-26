package com.loopers.domain.payment;

/** accepted=false는 실패가 아니라 결과 불명(타임아웃·서킷 Open) — PENDING 유지, 폴링/복구가 확정한다. */
public record GatewayResult(boolean accepted, String transactionKey) {

    public GatewayResult {
        if (accepted && transactionKey == null) {
            throw new IllegalArgumentException("접수(accepted)된 결과는 transactionKey가 있어야 합니다.");
        }
        if (!accepted && transactionKey != null) {
            throw new IllegalArgumentException("미접수(pending) 결과는 transactionKey를 가질 수 없습니다.");
        }
    }

    public static GatewayResult accepted(String transactionKey) {
        return new GatewayResult(true, transactionKey);
    }

    public static GatewayResult pending() {
        return new GatewayResult(false, null);
    }
}
