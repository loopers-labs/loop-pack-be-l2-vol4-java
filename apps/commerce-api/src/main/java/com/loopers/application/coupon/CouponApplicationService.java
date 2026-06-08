package com.loopers.application.coupon;

import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final MemberService memberService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public void issueCoupon(String loginId, Long couponTemplateId) {
        Member member = memberService.getMember(loginId);

        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        if (template.getExpiredAt().isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        if (issuedCouponRepository.existsByMemberIdAndCouponTemplateId(member.getId(), couponTemplateId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        issuedCouponRepository.save(IssuedCoupon.create(member.getId(), couponTemplateId));
    }

    @Transactional(readOnly = true)
    public List<MyCouponInfo> getMyCoupons(String loginId) {
        Member member = memberService.getMember(loginId);
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findAllByMemberId(member.getId());

        return issuedCoupons.stream()
            .map(issued -> {
                CouponTemplate template = couponTemplateRepository.findById(issued.getCouponTemplateId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다."));
                return MyCouponInfo.of(issued, template);
            })
            .toList();
    }

    // ADMIN
    @Transactional
    public CouponTemplate createTemplate(String name, com.loopers.domain.coupon.model.CouponType type,
                                          Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(CouponTemplate.create(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplate> getTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long couponTemplateId) {
        return couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional
    public CouponTemplate updateTemplate(Long couponTemplateId, String name,
                                          com.loopers.domain.coupon.model.CouponType type,
                                          Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        template.update(name, type, value, minOrderAmount, expiredAt);
        return couponTemplateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        couponTemplateRepository.delete(template);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> getIssuedCoupons(Long couponTemplateId, Pageable pageable) {
        couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        return issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }
}
