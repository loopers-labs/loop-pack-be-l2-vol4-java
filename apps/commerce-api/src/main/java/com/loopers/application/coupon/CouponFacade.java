package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserCouponInfo issueCoupon(String loginId, Long couponId) {
        UserModel user = loadUser(loginId);
        Coupon coupon = couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        UserCoupon issued = userCouponRepository.save(coupon.issueTo(user.getId(), now));
        return UserCouponInfo.from(issued, now);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(String loginId) {
        UserModel user = loadUser(loginId);
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByUserId(user.getId()).stream()
            .map(userCoupon -> UserCouponInfo.from(userCoupon, now))
            .toList();
    }

    @Transactional
    public CouponInfo createCoupon(CouponCommand.Create command) {
        return CouponInfo.from(couponRepository.save(command.toCoupon()));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getCoupons(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다.");
        }
        return couponRepository.findAll(page, size).stream()
            .map(CouponInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(loadCoupon(couponId));
    }

    @Transactional
    public CouponInfo updateCoupon(Long couponId, CouponCommand.Update command) {
        Coupon coupon = loadCoupon(couponId);
        coupon.update(command.name(), CouponType.from(command.type()), command.value(),
            command.minOrderAmount(), command.expiredAt());
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = loadCoupon(couponId);
        coupon.delete(); // soft delete = 신규 발급 중단 (기발급 쿠폰은 스냅샷으로 영향 없음)
        couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssuedCoupons(Long couponId, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다.");
        }
        loadCoupon(couponId); // 존재 검증
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByCouponId(couponId, page, size).stream()
            .map(userCoupon -> UserCouponInfo.from(userCoupon, now))
            .toList();
    }

    private Coupon loadCoupon(Long couponId) {
        return couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }

    private UserModel loadUser(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }
}
