package com.loopers.coupon.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponTemplateId(Long value) {

    public CouponTemplateId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 비어있을 수 없습니다.");
        }
    }

    public static CouponTemplateId of(Long value) {
        return new CouponTemplateId(value);
    }
}
