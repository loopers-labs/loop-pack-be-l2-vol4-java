package com.loopers.application.coupon;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CouponFacade {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponCreateInfo createCoupon(
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime now
    ) {
        CouponModel newCoupon = CouponModel.builder()
            .rawName(name)
            .type(discountType)
            .rawValue(discountValue)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(expiredAt)
            .now(now)
            .build();

        return CouponCreateInfo.from(couponRepository.save(newCoupon));
    }

    public CouponUpdateInfo updateCoupon(
        Long couponId,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime now
    ) {
        CouponModel coupon = couponRepository.getActiveById(couponId);
        coupon.update(name, discountType, discountValue, minOrderAmount, expiredAt, now);

        return CouponUpdateInfo.from(coupon);
    }

    public void deleteCoupon(Long couponId) {
        couponRepository.findActiveById(couponId).ifPresent(CouponModel::delete);
    }

    public UserCouponIssueInfo issueCoupon(Long userId, Long couponId, ZonedDateTime now) {
        UserModel user = userRepository.getActiveById(userId);
        CouponModel coupon = couponRepository.getActiveById(couponId);

        if (coupon.isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰 템플릿은 발급할 수 없습니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(user.getId(), coupon.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        UserCouponModel issuedCoupon = UserCouponModel.issue(user.getId(), coupon);

        return UserCouponIssueInfo.from(userCouponRepository.save(issuedCoupon));
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminInfo> readCoupons(int page, int size) {
        return couponRepository.findActiveByPage(page, size)
            .map(CouponAdminInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponAdminInfo readCoupon(Long couponId) {
        return CouponAdminInfo.from(couponRepository.getActiveById(couponId));
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> readMyCoupons(Long userId, ZonedDateTime now) {
        return userCouponRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(userCoupon -> UserCouponInfo.of(userCoupon, now))
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<CouponIssueInfo> readCouponIssues(Long couponId, int page, int size, ZonedDateTime now) {
        CouponModel coupon = couponRepository.getActiveById(couponId);

        return userCouponRepository.findByCouponIdOrderByCreatedAtDesc(coupon.getId(), page, size)
            .map(userCoupon -> CouponIssueInfo.of(userCoupon, now));
    }
}
