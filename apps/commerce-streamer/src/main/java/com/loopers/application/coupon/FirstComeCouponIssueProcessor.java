package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestRecord;
import com.loopers.domain.coupon.IssueRejectReason;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.metrics.EventHandledJpaEntity;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class FirstComeCouponIssueProcessor {

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public boolean process(CouponIssueRequestMessage event) {
        if (eventHandledJpaRepository.existsById(event.eventId())) {
            meterRegistry.counter("coupon_issue_consume_total", "result", "duplicate").increment();
            return false;
        }

        CouponIssueRequestJpaEntity requestEntity = couponIssueRequestJpaRepository.findByRequestIdAndDeletedAtIsNull(event.requestId())
            .orElseThrow(() -> new IllegalStateException("coupon issue request not found: " + event.requestId()));
        CouponIssueRequestRecord request = requestEntity.toRecord();
        if (!request.isRequested()) {
            markHandled(event);
            meterRegistry.counter("coupon_issue_consume_total", "result", "already_processed").increment();
            return false;
        }

        CouponJpaEntity coupon = couponJpaRepository.findById(event.couponId())
            .orElseThrow(() -> new IllegalStateException("coupon not found: " + event.couponId()));
        ZonedDateTime now = ZonedDateTime.now();

        if (issuedCouponJpaRepository.findByCouponIdAndUserLoginIdAndDeletedAtIsNull(event.couponId(), event.userLoginId()).isPresent()) {
            reject(requestEntity, request, IssueRejectReason.ALREADY_ISSUED, now);
            markHandled(event);
            return true;
        }

        IssuedCouponJpaEntity issuedCoupon;
        try {
            issuedCoupon = issuedCouponJpaRepository.saveAndFlush(
                IssuedCouponJpaEntity.issue(event.couponId(), event.userLoginId(), coupon.getExpiredAt())
            );
        } catch (DataIntegrityViolationException exception) {
            reject(requestEntity, request, IssueRejectReason.ALREADY_ISSUED, now);
            markHandled(event);
            return true;
        }

        if (couponJpaRepository.increaseIssuedCount(event.couponId()) == 0) {
            issuedCouponJpaRepository.delete(issuedCoupon);
            reject(requestEntity, request, IssueRejectReason.SOLD_OUT, now);
            markHandled(event);
            return true;
        }

        request.issue(issuedCoupon.getId(), now);
        requestEntity.update(request);
        couponIssueRequestJpaRepository.save(requestEntity);
        markHandled(event);
        meterRegistry.counter("coupon_issue_consume_total", "result", "issued").increment();
        return true;
    }

    private void reject(
        CouponIssueRequestJpaEntity requestEntity,
        CouponIssueRequestRecord request,
        IssueRejectReason reason,
        ZonedDateTime now
    ) {
        request.reject(reason, now);
        requestEntity.update(request);
        couponIssueRequestJpaRepository.save(requestEntity);
        meterRegistry.counter("coupon_issue_consume_total", "result", reason.name()).increment();
    }

    private void markHandled(CouponIssueRequestMessage event) {
        eventHandledJpaRepository.save(EventHandledJpaEntity.handled(event.eventId(), event.eventType()));
    }
}
