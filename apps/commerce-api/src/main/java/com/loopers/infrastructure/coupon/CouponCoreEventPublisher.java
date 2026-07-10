package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponEventPublisher;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponCoreEventPublisher implements CouponEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(CouponIssueRequestedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
