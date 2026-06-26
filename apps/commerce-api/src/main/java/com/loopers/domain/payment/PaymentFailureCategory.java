package com.loopers.domain.payment;

/**
 * 결제 실패 분류. "사용자가 다른 수단으로 다시 시도 가능한가" 를 기준으로 둔다.
 *
 *  - RECOVERABLE : 한도초과·잘못된 카드 등 → 사용자가 다른 카드로 즉시 재시도 가능
 *                  → Order 와 재고는 유지, Payment 만 FAILED 로 마킹.
 *                  → 사용자에게는 "다른 카드로 다시 시도해주세요" 안내.
 *  - TERMINAL    : 시스템 오류·타임아웃·라우팅 실패 등 → 같은 주문에 재시도 불가
 *                  → Order FAILED + 재고 복구 (기존 흐름).
 *
 * 분류 기준은 PG 응답 reason 텍스트 기반. PG 가 명시적인 에러 코드를 제공한다면 더 안정적이지만,
 * PG-Simulator 는 reason 메시지만 주므로 키워드로 분류한다.
 */
public enum PaymentFailureCategory {
    RECOVERABLE,
    TERMINAL,
    ;

    public static PaymentFailureCategory classify(String reason) {
        if (reason == null) {
            return TERMINAL;
        }
        // PG-Simulator 의 도메인 실패 reason: "한도초과입니다.", "잘못된 카드입니다. 다른 카드를 선택해주세요."
        if (reason.contains("한도") || reason.contains("카드")) {
            return RECOVERABLE;
        }
        return TERMINAL;
    }

    public boolean isRecoverable() {
        return this == RECOVERABLE;
    }
}
