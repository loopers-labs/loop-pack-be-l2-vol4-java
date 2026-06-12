package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿠폰 유스케이스 Application Service (스타일 2).
 *
 * <p>대고객(발급/내 쿠폰 목록)과 어드민(템플릿 CRUD/발급내역)을 모두 담당한다.
 * 쿠폰 사용(주문 시 확정)은 주문 트랜잭션의 일부라 {@link com.loopers.application.order.OrderTransactionService}가 처리한다.
 *
 * <p>발급 시 템플릿의 혜택이 발급분으로 스냅샷되므로("발급은 그 시점의 약속"),
 * 발급 이후의 조회/사용은 템플릿을 재조회하지 않는다.
 */
@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // ===== 대고객 =====

    /**
     * 쿠폰 발급 (템플릿당 유저 1장) — 발급 시점 혜택을 스냅샷한다.
     *
     * <p>사전 중복 체크 + {@code (user_id, coupon_id)} UK 를 이중 방어선으로 둔다.
     * 동시 발급 요청으로 UK 위반이 나면 CONFLICT 로 변환한다.
     */
    @Transactional
    public UserCouponInfo issue(Long userId, Long couponTemplateId) {
        CouponModel template = couponRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponTemplateId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        UserCouponModel userCoupon = UserCouponModel.issue(userId, template);
        try {
            userCouponRepository.saveAndFlush(userCoupon);
        } catch (DataIntegrityViolationException e) {
            // 동시 발급 UK 위반 — 발급은 1장만 유효
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.", e);
        }
        return UserCouponInfo.from(userCoupon, now);
    }

    /**
     * 내 쿠폰 목록 (AVAILABLE/USED/EXPIRED).
     *
     * <p>혜택이 발급분에 스냅샷되어 있어 템플릿 조인/일괄 조회 없이 발급분만으로 완성된다.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByUserId(userId).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    // ===== 어드민 =====

    @Transactional
    public CouponInfo createTemplate(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponModel coupon = new CouponModel(name, type, value, minOrderAmount, expiredAt);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public CouponInfo getTemplate(Long couponId) {
        return CouponInfo.from(findTemplateOrThrow(couponId));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getTemplates(int page, int size) {
        return couponRepository.findAll(page, size).stream()
            .map(CouponInfo::from)
            .toList();
    }

    /**
     * 템플릿 수정 — 이미 발급된 쿠폰의 혜택(스냅샷)에는 영향을 주지 않으며, 이후 발급분에만 적용된다.
     */
    @Transactional
    public CouponInfo updateTemplate(Long couponId, String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        coupon.update(name, type, value, minOrderAmount, expiredAt);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteTemplate(Long couponId) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        if (userCouponRepository.existsByCouponId(couponId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 발급된 쿠폰이 있어 삭제할 수 없습니다.");
        }
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssues(Long couponId, int page, int size) {
        findTemplateOrThrow(couponId);   // 템플릿 존재 검증
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByCouponId(couponId, page, size).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    private CouponModel findTemplateOrThrow(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }
}
