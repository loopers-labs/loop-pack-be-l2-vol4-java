package com.loopers.domain.payment;

/** NOT_FOUND(거래 없음)와 UNREACHABLE(PG 장애)을 구분해, 장애 시 섣부른 취소를 막는다. */
public record GatewayLookup(Result result, String transactionKey, String status, String reason) {

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
