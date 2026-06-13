package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public IssuedCoupon issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        return issuedCouponRepository.save(new IssuedCoupon(couponId, userId, coupon.getExpiredAt()));
    }

    public List<CouponInfo.MyCoupon> getUserCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId).stream()
            .map(CouponInfo.MyCoupon::of)
            .toList();
    }

    // OrderFacade의 @Transactional에 합류하여 쿠폰 사용·재고 차감·주문 생성이 단일 트랜잭션으로 묶인다.
    @Transactional
    public BigDecimal use(Long issuedCouponId, Long userId, BigDecimal totalAmount) {
        try {
            IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 쿠폰입니다."));
            if (!issuedCoupon.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
            }
            Coupon coupon = couponRepository.findById(issuedCoupon.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보가 존재하지 않습니다."));
            BigDecimal discount = coupon.calculateDiscount(totalAmount);
            issuedCoupon.use();
            issuedCouponRepository.save(issuedCoupon);
            return discount;
        } catch (OptimisticLockingFailureException e) {
            throw new CoreException(ErrorType.CONFLICT, "사용 불가능한 쿠폰입니다.");
        }
    }

    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    public Page<Coupon> getCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public Coupon createCoupon(CouponCommand.Create command) {
        Coupon coupon = new Coupon(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt());
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon updateCoupon(Long couponId, CouponCommand.Update command) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        coupon.update(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt());
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        coupon.delete();
        couponRepository.save(coupon);
    }

    public Page<IssuedCoupon> getIssuedCoupons(Long couponId, Pageable pageable) {
        getCoupon(couponId);
        return issuedCouponRepository.findAllByCouponId(couponId, pageable);
    }
}
