package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponService userCouponService;

    @Transactional
    public CouponInfo create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return CouponInfo.from(couponTemplateService.create(name, type, value, minOrderAmount, toZoned(expiredAt)));
    }

    /** 어드민용 — 삭제된 템플릿 포함 */
    public CouponInfo get(UUID id) {
        return CouponInfo.from(couponTemplateService.get(id));
    }

    /** 어드민 목록 — 삭제된 템플릿 포함, 페이징 */
    public Page<CouponInfo> getList(Pageable pageable) {
        return couponTemplateService.getList(pageable).map(CouponInfo::from);
    }

    @Transactional
    public CouponInfo update(UUID id, String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return CouponInfo.from(couponTemplateService.update(id, name, type, value, minOrderAmount, toZoned(expiredAt)));
    }

    @Transactional
    public void delete(UUID id) {
        couponTemplateService.delete(id);
    }

    /** 쿠폰 발급 — 활성 템플릿 조회 후 발급. 만료 템플릿이면 거부. */
    @Transactional
    public UserCouponInfo issue(UUID userId, UUID templateId) {
        CouponTemplateModel template = couponTemplateService.getActive(templateId);
        ZonedDateTime now = ZonedDateTime.now();
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return UserCouponInfo.from(userCouponService.issue(userId, template), now);
    }

    /** 내 쿠폰 목록 — 상태는 조회 시점 기준 동적 판정 */
    public List<UserCouponInfo> getMyCoupons(UUID userId) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponService.getMyCoupons(userId).stream()
            .map(c -> UserCouponInfo.from(c, now))
            .toList();
    }

    /** 어드민 — 템플릿별 발급 내역 */
    public Page<UserCouponInfo> getIssuesByTemplate(UUID templateId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponService.getIssuesByTemplate(templateId, pageable).map(c -> UserCouponInfo.from(c, now));
    }

    private ZonedDateTime toZoned(LocalDateTime expiredAt) {
        return expiredAt.atZone(ZoneId.systemDefault());
    }
}
