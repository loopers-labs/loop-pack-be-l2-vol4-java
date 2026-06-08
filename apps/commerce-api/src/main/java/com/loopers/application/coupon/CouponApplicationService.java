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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 쿠폰 유스케이스 Application Service (스타일 2).
 *
 * <p>대고객(발급/내 쿠폰 목록)과 어드민(템플릿 CRUD/발급내역)을 모두 담당한다.
 * 쿠폰 사용(주문 시 차감)은 주문 트랜잭션의 일부라 {@link com.loopers.application.order.OrderApplicationService}가 처리한다.
 */
@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // ===== 대고객 =====

    /**
     * 쿠폰 발급 (템플릿당 유저 1장).
     *
     * <p>사전 중복 체크 + {@code (user_id, coupon_id)} UK 를 이중 방어선으로 둔다.
     * 동시 발급 요청으로 UK 위반이 나면 CONFLICT 로 변환한다.
     */
    @Transactional
    public UserCouponInfo issue(Long userId, Long couponTemplateId) {
        CouponModel template = couponRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

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
        return UserCouponInfo.from(userCoupon, template, ZonedDateTime.now());
    }

    /**
     * 내 쿠폰 목록 (AVAILABLE/USED/EXPIRED).
     *
     * <p>발급분 → 템플릿 IN 일괄 조회로 N+1 을 회피한다.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        List<UserCouponModel> userCoupons = userCouponRepository.findByUserId(userId);
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        List<Long> templateIds = userCoupons.stream()
            .map(UserCouponModel::getCouponId)
            .distinct()
            .toList();
        Map<Long, CouponModel> templates = couponRepository.findAllByIds(templateIds).stream()
            .collect(Collectors.toMap(CouponModel::getId, Function.identity()));

        ZonedDateTime now = ZonedDateTime.now();
        return userCoupons.stream()
            .filter(uc -> templates.containsKey(uc.getCouponId()))   // 삭제된 템플릿 참조 건너뜀 (NPE 방지)
            .map(uc -> UserCouponInfo.from(uc, templates.get(uc.getCouponId()), now))
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
        CouponModel template = findTemplateOrThrow(couponId);
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findByCouponId(couponId, page, size).stream()
            .map(uc -> UserCouponInfo.from(uc, template, now))
            .toList();
    }

    private CouponModel findTemplateOrThrow(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }
}
