package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CouponCommandService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponResult.Template createTemplate(CouponCommand.CreateTemplate command) {
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 등록 요청은 필수입니다.");
        }
        return CouponResult.Template.from(couponTemplateRepository.save(new CouponTemplate(
            command.name(),
            command.type(),
            command.value(),
            command.minOrderAmount(),
            command.maxIssuesPerUser(),
            command.expiredAt()
        )));
    }

    @Transactional
    public CouponResult.Template updateTemplate(Long couponTemplateId, CouponCommand.UpdateTemplate command) {
        validateCouponTemplateId(couponTemplateId);
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 수정 요청은 필수입니다.");
        }

        CouponTemplate couponTemplate = getTemplate(couponTemplateId);
        couponTemplate.update(
            command.name(),
            command.type(),
            command.value(),
            command.minOrderAmount(),
            command.maxIssuesPerUser(),
            command.expiredAt()
        );
        return CouponResult.Template.from(couponTemplateRepository.save(couponTemplate));
    }

    @Transactional
    public void deleteTemplate(Long couponTemplateId) {
        CouponTemplate couponTemplate = getTemplate(couponTemplateId);
        couponTemplate.delete();
        couponTemplateRepository.save(couponTemplate);
    }

    @Transactional
    public CouponResult.Issued issue(Long couponTemplateId, String userId) {
        validateCouponTemplateId(couponTemplateId);
        validateUserId(userId);

        CouponTemplate couponTemplate = couponTemplateRepository.findActiveForUpdate(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 발급 가능한 쿠폰 템플릿을 찾을 수 없습니다."
            ));
        couponTemplate.ensureIssuable(ZonedDateTime.now());

        long issuedCount = issuedCouponRepository.countByCouponTemplateIdAndUserId(couponTemplateId, userId);
        if (issuedCount >= couponTemplate.getMaxIssuesPerUser()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자별 쿠폰 발급 한도를 초과했습니다.");
        }

        IssuedCoupon issuedCoupon = issuedCouponRepository.save(new IssuedCoupon(couponTemplateId, userId, couponTemplate));
        return CouponResult.Issued.from(issuedCoupon, couponTemplate.getName(), ZonedDateTime.now());
    }

    private CouponTemplate getTemplate(Long couponTemplateId) {
        validateCouponTemplateId(couponTemplateId);
        return couponTemplateRepository.find(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 쿠폰 템플릿을 찾을 수 없습니다."
            ));
    }

    private void validateCouponTemplateId(Long couponTemplateId) {
        if (couponTemplateId == null || couponTemplateId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }
}
