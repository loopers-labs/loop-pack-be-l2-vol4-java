package com.loopers.domain.payment;

/** NOT_FOUND(거래 없음)와 UNREACHABLE(PG 장애)을 구분해, 장애 시 섣부른 취소를 막는다. */
public record GatewayLookup(Result result, String transactionKey, String status, String reason) {

    public GatewayLookup {
        if (result == Result.FOUND && (transactionKey == null || status == null)) {
            throw new IllegalArgumentException("FOUND 결과는 transactionKey와 status가 있어야 합니다.");
        }
        if (result != Result.FOUND && (transactionKey != null || status != null || reason != null)) {
            throw new IllegalArgumentException("FOUND가 아닌 결과는 payload를 가질 수 없습니다.");
        }
    }

    public enum Result { FOUND, NOT_FOUND, UNREACHABLE }

    public static GatewayLookup found(String transactionKey, String status, String reason) {
        return new GatewayLookup(Result.FOUND, transactionKey, status, reason);
    }

    public static GatewayLookup notFound() {
        return new GatewayLookup(Result.NOT_FOUND, null, null, null);
    }

    public static GatewayLookup unreachable() {
        return new GatewayLookup(Result.UNREACHABLE, null, null, null);
    }
}
