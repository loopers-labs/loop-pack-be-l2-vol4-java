package com.loopers.domain.payment;

/**
 * 주문 기준 PG 조회 결과. 세 상태를 구분해 안전한 복구 판단을 돕는다.
 * - FOUND: PG에 거래가 있음 (거래키 backfill 후 확정)
 * - NOT_FOUND: PG가 응답했고 거래가 없음 (미접수 — 유예시간 경과 시 취소)
 * - UNREACHABLE: PG 장애로 알 수 없음 (다음 주기로 미룸 — 섣불리 취소하지 않음)
 */
public record GatewayLookup(Result result, String transactionKey, String status) {

    public enum Result { FOUND, NOT_FOUND, UNREACHABLE }

    public static GatewayLookup found(String transactionKey, String status) {
        return new GatewayLookup(Result.FOUND, transactionKey, status);
    }

    public static GatewayLookup notFound() {
        return new GatewayLookup(Result.NOT_FOUND, null, null);
    }

    public static GatewayLookup unreachable() {
        return new GatewayLookup(Result.UNREACHABLE, null, null);
    }
}
