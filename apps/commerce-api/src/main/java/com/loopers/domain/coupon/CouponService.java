package com.loopers.domain.coupon;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 도메인 서비스. 발급 / 사용 / 조회 / Admin CRUD 책임.
 * 트랜잭션 경계: 메서드별 명시. 조회는 readOnly = true.
 * useCoupon 은 OrderFacade 의 트랜잭션에 REQUIRED 로 합류해 한 트랜잭션 안에서 동작한다.
 */
@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // ============================== 대고객 ==============================

    /**
     * 사용자에게 쿠폰을 발급한다. (userId, couponId) UNIQUE 로 1인 1장.
     * 만료된 쿠폰 발급은 거부한다.
     */
    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        Coupon coupon = couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));

        if (coupon.isExpired(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
        return userCouponRepository.save(UserCoupon.issue(userId, couponId));
    }

    /**
     * 주문 시 쿠폰을 사용해 할인 금액을 계산하고 USED 로 전이한다.
     * OrderFacade 의 @Transactional 안에서 호출되어 같은 트랜잭션으로 묶인다.
     * 타 유저 소유 / 미존재는 NOT_FOUND 로 통일 (존재 노출 방지).
     * 동시 사용 시 @Version 충돌(OptimisticLockingFailureException) → 전체 롤백.
     */
    @Transactional
    public Money useCoupon(Long userId, Long userCouponId, Money orderTotal) {
        UserCoupon userCoupon = userCouponRepository.find(userCouponId)
            .filter(uc -> uc.belongsTo(userId))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + userCouponId + "] 쿠폰을 찾을 수 없습니다."));

        Coupon coupon = couponRepository.find(userCoupon.getCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "쿠폰 템플릿을 찾을 수 없습니다."));

        if (coupon.isExpired(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        Money discount = coupon.discount(orderTotal);
        userCoupon.use(); // 상태 전이 → 같은 트랜잭션에서 dirty checking 으로 flush
        return discount;
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getMyCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long couponId) {
        return couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }

    // ============================== Admin ==============================

    @Transactional
    public Coupon createCoupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return couponRepository.save(Coupon.create(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public List<Coupon> getCoupons(int page, int size) {
        return couponRepository.findAll(page, size);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        getCoupon(couponId); // 존재 검증
        couponRepository.delete(couponId);
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getIssues(Long couponId, int page, int size) {
        return userCouponRepository.findByCouponId(couponId, page, size);
    }
}
