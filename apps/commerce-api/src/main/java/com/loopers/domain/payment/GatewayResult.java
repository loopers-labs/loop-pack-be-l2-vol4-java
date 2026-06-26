package com.loopers.domain.payment;

/**
 * PG 접수 결과.
 * - ACCEPTED: PG가 거래키를 발급함.
 * - PENDING : 타임아웃·응답유실 등 접수 여부 불명 — PENDING 유지, 폴링/복구가 확정한다.
 * - REJECTED: 서킷 OPEN으로 PG에 호출조차 못 감(미접수 확정) — 즉시 실패 처리한다.
 */
public record GatewayResult(Outcome outcome, String transactionKey) {

    public enum Outcome { ACCEPTED, PENDING, REJECTED }

    public GatewayResult {
        if (outcome == Outcome.ACCEPTED && (transactionKey == null || transactionKey.isBlank())) {
            throw new IllegalArgumentException("접수(ACCEPTED)된 결과는 transactionKey가 있어야 합니다.");
        }
        if (outcome != Outcome.ACCEPTED && transactionKey != null) {
            throw new IllegalArgumentException("미접수 결과는 transactionKey를 가질 수 없습니다.");
        }
    }

    public static GatewayResult accepted(String transactionKey) {
        return new GatewayResult(Outcome.ACCEPTED, transactionKey);
    }

    public static GatewayResult pending() {
        return new GatewayResult(Outcome.PENDING, null);
    }

    public static GatewayResult rejected() {
        return new GatewayResult(Outcome.REJECTED, null);
    }

    public boolean isAccepted() {
        return outcome == Outcome.ACCEPTED;
    }

    public boolean isRejected() {
        return outcome == Outcome.REJECTED;
    }
}
