package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class CouponDomainService {

    public long calculateDiscount(CouponTemplateModel template, long orderAmount) {
        if (template == null) {
            return 0L;
        }
        if (template.getType() == CouponType.FIXED) {
            return Math.min(template.getValue(), orderAmount);
        }
        return orderAmount * template.getValue() / 100;
    }

    public void validateMinOrderAmount(CouponTemplateModel template, long orderAmount) {
        if (template == null) {
            return;
        }
        Long minOrderAmount = template.getMinOrderAmount();
        if (minOrderAmount == null) {
            return;
        }
        if (orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액 " + minOrderAmount + "원 이상이어야 쿠폰을 사용할 수 있습니다.");
        }
    }
}
