package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    public Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }

    public List<IssuedCouponModel> getMyIssuedCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId);
    }

    public IssuedCouponModel issue(Long couponTemplateId, Long userId) {
        if (issuedCouponRepository.existsByCouponTemplateIdAndUserId(couponTemplateId, userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
        return issuedCouponRepository.save(new IssuedCouponModel(couponTemplateId, userId));
    }

    public IssuedCouponModel getById(Long issuedCouponId) {
        return issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    public IssuedCouponModel getMyIssuedCoupon(Long issuedCouponId, Long userId) {
        IssuedCouponModel issued = getById(issuedCouponId);
        issued.validateOwner(userId);
        return issued;
    }

    public void use(Long issuedCouponId) {
        IssuedCouponModel issued = getById(issuedCouponId);
        issued.use();
        issuedCouponRepository.save(issued);
    }
}
