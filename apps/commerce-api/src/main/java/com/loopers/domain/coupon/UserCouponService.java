package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponService couponService;
    private final CouponUsePolicy couponUsePolicy;

    @Transactional(readOnly = true)
    public UserCouponModel get(Long userCouponId) {
        return userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + userCouponId + "] 유저 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public UserCouponModel issue(Long couponId, Long userId) {
        CouponModel coupon = couponService.get(couponId);
        coupon.validateNotExpired();

        try {
            return userCouponRepository.save(new UserCouponModel(userId, coupon));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<UserCouponModel> getMyList(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<UserCouponModel> getListByCouponId(Long couponId, Pageable pageable) {
        return userCouponRepository.findAllByCouponId(couponId, pageable);
    }

    public record UseResult(long originalAmount, long discountAmount) {}

    @Transactional
    public UseResult use(Long userCouponId, Long userId, long originalTotal) {
        if (userCouponId == null) return new UseResult(originalTotal, 0L);
        UserCouponModel userCoupon = get(userCouponId);
        long discount = userCoupon.getCoupon().calculateDiscount(originalTotal);
        couponUsePolicy.validate(userCoupon, userId);
        boolean used = userCouponRepository.useIfIssued(userCouponId, ZonedDateTime.now());
        if (!used) throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        return new UseResult(originalTotal, discount);
    }

    @Transactional
    public void revert(Long userCouponId) {
        if (userCouponId == null) return;
        get(userCouponId).revert();
    }
}
