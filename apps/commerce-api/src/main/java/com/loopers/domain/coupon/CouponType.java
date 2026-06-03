package com.loopers.domain.coupon;

/**
 * 쿠폰 할인 방식 (01 §3.1, 03 §2). 방식별 할인 계산을 enum 메서드로 캡슐화해
 * CouponModel에서 if(type==FIXED)... 분기를 제거한다. 새 타입 추가 시 이 enum만 확장.
 */
public enum CouponType {

    /** 정액 — 정해진 금액만큼 깎는다. 주문 금액을 넘지 않도록 min으로 상한. */
    FIXED {
        @Override
        public long discount(long value, long originalAmount) {
            return Math.min(value, originalAmount);
        }
    },

    /** 정률 — 주문 금액의 value(%)만큼 깎는다. 원 단위 미만은 버린다(floor, 01 §7.1 Q6). */
    RATE {
        @Override
        public long discount(long value, long originalAmount) {
            return originalAmount * value / 100;
        }
    };

    /** 적용 전 금액(originalAmount)에 대해 깎이는 할인 금액을 계산한다. */
    public abstract long discount(long value, long originalAmount);
}
