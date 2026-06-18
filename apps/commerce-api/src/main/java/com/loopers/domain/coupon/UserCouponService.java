package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 발급분(UserCoupon) — 발급/조회/주문 적용/원복. 템플릿(CouponService)과 협력해
 * 할인 계산·발급 가능 검증을 조정한다(03 §1 도메인 서비스 협력).
 */
@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponService couponService;

    /** 쿠폰 발급 (UC-13). 발급 가능한 템플릿일 때만 새 발급분을 만든다. 중복 발급 허용(§9 Q4). */
    @Transactional
    public IssuedCouponView issue(Long userId, Long couponId) {
        ZonedDateTime now = ZonedDateTime.now();
        CouponModel template = couponService.getIssuableTemplate(couponId, now);
        UserCouponModel saved = userCouponRepository.save(new UserCouponModel(userId, couponId));
        return IssuedCouponView.of(saved, template, now);
    }

    /** 내 쿠폰 목록 (UC-14) — 발급분 + 템플릿 batch 조합으로 상태를 파생한다(N+1 회피). */
    @Transactional(readOnly = true)
    public List<IssuedCouponView> getMyCoupons(Long userId, int page, int size) {
        PagePolicy.validate(page, size);
        List<UserCouponModel> userCoupons = userCouponRepository.findByUserId(userId, page, size);
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        List<Long> couponIds = userCoupons.stream().map(UserCouponModel::getCouponId).distinct().toList();
        Map<Long, CouponModel> templates = couponService.findByIds(couponIds).stream()
                .collect(Collectors.toMap(CouponModel::getId, Function.identity()));
        ZonedDateTime now = ZonedDateTime.now();
        return userCoupons.stream()
                .map(uc -> IssuedCouponView.of(uc, templates.get(uc.getCouponId()), now))
                .toList();
    }

    /** 특정 템플릿의 발급 내역 (UC-16 Admin). */
    @Transactional(readOnly = true)
    public List<UserCouponModel> getIssues(Long couponId, int page, int size) {
        PagePolicy.validate(page, size);
        return userCouponRepository.findByCouponId(couponId, page, size);
    }

    /**
     * 주문 적용 (UC-17) — 사용 가능 발급분 선택 → 템플릿 검증·할인 계산 → use() 사용 처리.
     * 비관적 락(SELECT ... FOR UPDATE) 기본 경로: 발급분 행을 잠그고 읽어, 동시 사용 시 경합
     * 트랜잭션이 선행 커밋까지 대기 → 이중 사용을 차단한다(UC-20 §5-B). 핫스팟 주문 경로인 만큼
     * 처리량보다 정합성·예측가능성을 택한 정책으로, 재고 차감(StockService)과 동일하다.
     * 낙관적 락(@Version) 경로(findFirstAvailable)는 비교·학습용으로 인프라에 남겨 둔다(§5-A).
     * - 사용 가능 발급분 없음(미보유/전부 USED/EXPIRED/타유저) → NOT_FOUND (§2 격리, §7.2)
     * - 만료/최소 주문 금액 미달 → BAD_REQUEST (CouponModel.ensureUsableAt)
     */
    @Transactional
    public AppliedCoupon useForOrder(Long userId, Long couponId, long originalAmount) {
        UserCouponModel userCoupon = userCouponRepository.findFirstAvailableForUpdate(userId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용 가능한 쿠폰이 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        CouponModel coupon = couponService.getActiveTemplate(couponId);
        coupon.ensureUsableAt(now, originalAmount);

        long discount = coupon.calculateDiscount(originalAmount);
        userCoupon.use();
        UserCouponModel saved = userCouponRepository.save(userCoupon);
        return new AppliedCoupon(saved.getId(), discount);
    }

    /** 사용 원복 (UC-19, 결제 실패 시). 멱등 — 부재면 무시. */
    @Transactional
    public void restore(Long userCouponId) {
        userCouponRepository.find(userCouponId).ifPresent(uc -> {
            uc.restore();
            userCouponRepository.save(uc);
        });
    }
}
