package com.loopers.fixture;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 테스트 픽스처 — Object Mother 패턴
 *
 * 사용 원칙:
 *   - createModel()                 : 완전히 유효한 기본 객체가 필요할 때
 *   - 상수 직접 참조 (NAME, VALUE 등) : "한 필드만 깨뜨리기" 패턴에서 나머지 VALID 값으로 사용
 *   - createModel(name) 오버로드      : 중복 이름 시나리오 등에서 이름만 변경 시
 */
public class CouponTemplateFixture {

    public static final String NAME = "신규가입 10% 할인";
    public static final CouponType TYPE = CouponType.RATE;
    public static final Long VALUE = 10L;
    public static final Long MIN_ORDER_AMOUNT = 10000L;
    public static final ZonedDateTime EXPIRED_AT = ZonedDateTime.now().plusDays(30);

    public static CouponTemplateModel createModel() {
        return new CouponTemplateModel(NAME, TYPE, VALUE, MIN_ORDER_AMOUNT, EXPIRED_AT);
    }

    public static CouponTemplateModel createModel(String name) {
        return new CouponTemplateModel(name, TYPE, VALUE, MIN_ORDER_AMOUNT, EXPIRED_AT);
    }
}
