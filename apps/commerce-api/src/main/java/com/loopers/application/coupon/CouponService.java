package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional(readOnly = true)
    public Page<CouponInfo> getAll(Pageable pageable) {
        return couponRepository.findAllActive(pageable).map(CouponInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponInfo getById(Long id) {
        return CouponInfo.from(findActiveById(id));
    }

    @Transactional
    public CouponInfo create(CouponCreateCommand command) {
        return CouponInfo.from(couponRepository.save(command.toDomain()));
    }

    @Transactional
    public CouponInfo update(Long id, CouponUpdateCommand command) {
        CouponModel coupon = findActiveById(id);
        coupon.update(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt());
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void delete(Long id) {
        CouponModel coupon = findActiveById(id);
        coupon.delete();
        couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Page<UserCouponInfo> getIssues(Long couponId, Pageable pageable) {
        findActiveById(couponId);
        return userCouponRepository.findAllByCouponId(couponId, pageable).map(UserCouponInfo::from);
    }

    private CouponModel findActiveById(Long id) {
        return couponRepository.findById(id)
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
    }
}
