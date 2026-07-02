package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CouponInfo issue(Long userId, Long couponPolicyId) {
        return CouponInfo.from(couponService.issue(userId, couponPolicyId), ZonedDateTime.now());
    }

    /**
     * 선착순 쿠폰 발급을 비동기로 접수한다. 실제 발급은 하지 않고,
     * 발급요청(PENDING) 저장과 outbox 적재를 한 트랜잭션으로 묶어 At Least Once 발행을 보장한다.
     * 실제 발급(수량 차감 + UserCoupon 생성)은 coupon-issue-requests 를 소비하는 streamer 가 수행한다.
     */
    @Transactional
    public CouponIssueRequestInfo requestIssue(Long userId, Long couponId) {
        CouponPolicy policy = couponService.getPolicy(couponId);
        CouponIssueRequest request = couponIssueRequestRepository.save(CouponIssueRequest.pending(userId, couponId));
        outboxEventRepository.append(toOutboxEvent(request, policy));
        return CouponIssueRequestInfo.from(request);
    }

    /**
     * 발급 요청의 처리 상태를 조회한다(폴링). 본인 요청만 보이며, 타인 요청은 존재를 노출하지 않도록 NOT_FOUND 로 막는다.
     */
    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequest(Long userId, Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .filter(it -> it.isOwnedBy(userId))
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        return CouponIssueRequestInfo.from(request);
    }

    private OutboxEvent toOutboxEvent(CouponIssueRequest request, CouponPolicy policy) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(CouponIssueRequestMessage.of(request, policy));
            // aggregateId=couponPolicyId → Kafka key → 같은 쿠폰=같은 파티션(선착순 순서 보장)
            return OutboxEvent.of("Coupon", request.getCouponPolicyId(), "COUPON_ISSUE_REQUESTED", eventId, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("쿠폰 발급요청 payload 직렬화 실패 (requestId=" + request.getId() + ")", e);
        }
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getMyCoupons(userId).stream()
            .map(userCoupon -> CouponInfo.from(userCoupon, now))
            .toList();
    }

    @Transactional
    public CouponAdminInfo createPolicy(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return createPolicy(name, type, value, minOrderAmount, expiredAt, null);
    }

    @Transactional
    public CouponAdminInfo createPolicy(String name, CouponType type, long value, Long minOrderAmount,
                                        ZonedDateTime expiredAt, Long maxIssueCount) {
        return CouponAdminInfo.from(couponService.createPolicy(name, type, value, minOrderAmount, expiredAt, maxIssueCount));
    }

    @Transactional(readOnly = true)
    public CouponAdminInfo getPolicy(Long couponPolicyId) {
        return CouponAdminInfo.from(couponService.getPolicy(couponPolicyId));
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminInfo> getPolicies(int page, int size) {
        return couponService.getPolicies(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(CouponAdminInfo::from);
    }

    @Transactional
    public CouponAdminInfo updatePolicy(Long couponPolicyId, String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponAdminInfo.from(couponService.updatePolicy(couponPolicyId, name, minOrderAmount, expiredAt));
    }

    @Transactional
    public void deletePolicy(Long couponPolicyId) {
        couponService.deletePolicy(couponPolicyId);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getIssuedCoupons(Long couponPolicyId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getIssuedCoupons(couponPolicyId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(userCoupon -> IssuedCouponInfo.from(userCoupon, now));
    }
}
