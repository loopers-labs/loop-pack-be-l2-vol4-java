package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CouponQueryService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(readOnly = true)
    public PageResult<CouponResult.Template> getAdminTemplates(int page, int size) {
        validatePage(page, size);
        List<CouponResult.Template> items = couponTemplateRepository.findAll(page, size)
            .stream()
            .map(CouponResult.Template::from)
            .toList();
        return PageResult.of(items, page, size, couponTemplateRepository.countAll());
    }

    @Transactional(readOnly = true)
    public CouponResult.Template getAdminTemplate(Long couponTemplateId) {
        return CouponResult.Template.from(getTemplate(couponTemplateId));
    }

    @Transactional(readOnly = true)
    public PageResult<CouponResult.Issued> getAdminIssues(Long couponTemplateId, int page, int size) {
        validatePage(page, size);
        CouponTemplate couponTemplate = getTemplate(couponTemplateId);
        List<CouponResult.Issued> items = toIssuedResults(
            issuedCouponRepository.findByCouponTemplateId(couponTemplateId, page, size),
            couponTemplate.getName()
        );
        return PageResult.of(items, page, size, issuedCouponRepository.countByCouponTemplateId(couponTemplateId));
    }

    @Transactional(readOnly = true)
    public PageResult<CouponResult.Issued> getMyCoupons(String userId, int page, int size) {
        validateUserId(userId);
        validatePage(page, size);
        List<CouponResult.Issued> items = issuedCouponRepository.findByUserId(userId, page, size)
            .stream()
            .map(issuedCoupon -> CouponResult.Issued.from(
                issuedCoupon,
                getTemplate(issuedCoupon.getCouponTemplateId()).getName(),
                ZonedDateTime.now()
            ))
            .toList();
        return PageResult.of(items, page, size, issuedCouponRepository.countByUserId(userId));
    }

    private List<CouponResult.Issued> toIssuedResults(List<IssuedCoupon> issuedCoupons, String couponName) {
        ZonedDateTime now = ZonedDateTime.now();
        return issuedCoupons.stream()
            .map(issuedCoupon -> CouponResult.Issued.from(issuedCoupon, couponName, now))
            .toList();
    }

    private CouponTemplate getTemplate(Long couponTemplateId) {
        if (couponTemplateId == null || couponTemplateId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
        return couponTemplateRepository.find(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 쿠폰 템플릿을 찾을 수 없습니다."
            ));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
    }
}
