package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.FakeCouponIssueRequestRepository;
import com.loopers.domain.coupon.FakeCouponTemplateRepository;
import com.loopers.domain.event.CouponIssueRequestedEvent;
import com.loopers.support.event.RecordingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueRequestFacadeTest {

    private CouponIssueRequestFacade facade;
    private FakeCouponTemplateRepository templateRepo;
    private RecordingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        templateRepo = new FakeCouponTemplateRepository();
        CouponTemplateService templateService = new CouponTemplateService(templateRepo);
        FakeCouponIssueRequestRepository requestRepo = new FakeCouponIssueRequestRepository();
        CouponIssueRequestService requestService = new CouponIssueRequestService(requestRepo);
        publisher = new RecordingEventPublisher();
        facade = new CouponIssueRequestFacade(templateService, requestService, publisher);
    }

    @DisplayName("요청 접수 시 PENDING 요청 저장 + CouponIssueRequestedEvent 발행 (같은 트랜잭션).")
    @Test
    void enqueuePublishesEvent() {
        CouponTemplate t = templateRepo.save(new CouponTemplate(
            "선착순", CouponType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(1), 100L
        ));

        CouponIssueRequestInfo info = facade.enqueue(new CouponIssueRequestCriteria.Enqueue(100L, t.getId()));

        assertThat(info.status()).isEqualTo(CouponIssueStatus.PENDING);
        assertThat(info.requestId()).isNotBlank();
        assertThat(publisher.filter(CouponIssueRequestedEvent.class)).hasSize(1);
        assertThat(publisher.filter(CouponIssueRequestedEvent.class).get(0).requestId())
            .isEqualTo(info.requestId());
    }
}
