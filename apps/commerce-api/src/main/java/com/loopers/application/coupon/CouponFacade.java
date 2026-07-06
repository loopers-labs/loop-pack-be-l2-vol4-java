package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.money.Money;
import com.loopers.application.support.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponFacade {
    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CouponInfo register(String name, CouponType type, long value, BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        Discount discount = new Discount(type, value);
        Money minOrder = minOrderAmount == null ? Money.ZERO : new Money(minOrderAmount);
        Coupon coupon = couponService.register(name, discount, minOrder, expiredAt);
        return CouponInfo.from(coupon);
    }

    public CouponInfo update(Long couponId, String name, CouponType type, long value, BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        Discount discount = new Discount(type, value);
        Money minOrder = minOrderAmount == null ? Money.ZERO : new Money(minOrderAmount);
        Coupon coupon = couponService.update(couponId, name, discount, minOrder, expiredAt);
        return CouponInfo.from(coupon);
    }

    public void delete(Long couponId) {
        couponService.delete(couponId);
    }

    public PageResult<CouponIssueInfo> getIssues(Long couponId, int page, int size) {
        List<CouponIssueInfo> items = couponService.getIssues(couponId, page, size).stream()
            .map(CouponIssueInfo::from)
            .toList();
        return new PageResult<>(items, page, size, couponService.countIssues(couponId));
    }

    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCoupon(couponId));
    }

    public PageResult<CouponInfo> getCoupons(int page, int size) {
        List<CouponInfo> items = couponService.getCouponPage(page, size).stream()
            .map(CouponInfo::from)
            .toList();
        return new PageResult<>(items, page, size, couponService.countCoupons());
    }

    public IssuedCouponInfo issue(Long userId, Long couponId) {
        UserCoupon userCoupon = couponService.issue(userId, couponId);
        return IssuedCouponInfo.from(userCoupon);
    }

    /**
     * 선착순 발급 요청 접수 — 요청(원천 사실) 저장과 이벤트 발행(→outbox)만 하고 즉시 응답한다.
     * 실제 발급(수량 확인·중복 방지)은 Kafka Consumer 가 순차 처리한다.
     */
    @Transactional
    public CouponIssueRequestInfo requestIssue(Long userId, Long couponId) {
        couponService.getCoupon(couponId); // 존재하지 않는 쿠폰은 접수 전에 거른다
        CouponIssueRequest request = couponIssueRequestRepository.save(
            new CouponIssueRequest(UUID.randomUUID().toString(), couponId, userId));
        eventPublisher.publishEvent(CouponIssueRequestedEvent.from(request));
        return CouponIssueRequestInfo.from(request);
    }

    /** 발급 요청 결과 폴링. 타인의 요청은 존재 비노출(NOT_FOUND). */
    public CouponIssueRequestInfo getIssueRequest(Long userId, String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .filter(found -> found.getUserId().equals(userId))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[requestId = " + requestId + "] 발급 요청을 찾을 수 없습니다."));
        return CouponIssueRequestInfo.from(request);
    }

    public List<UserCouponInfo> getMyCoupons(Long userId) {
        List<UserCoupon> userCoupons = couponService.getMyCoupons(userId);
        if (userCoupons.isEmpty()) {
            return List.of();
        }

        List<Long> couponIds = userCoupons.stream()
            .map(UserCoupon::getCouponId)
            .distinct()
            .toList();
        Map<Long, Coupon> coupons = couponService.getCoupons(couponIds).stream()
            .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        return userCoupons.stream()
            .map(userCoupon -> {
                Coupon coupon = coupons.get(userCoupon.getCouponId());
                CouponStatus status = userCoupon.resolveStatus(coupon.getExpiredAt(), now);
                return UserCouponInfo.of(userCoupon, coupon, status);
            })
            .toList();
    }
}
