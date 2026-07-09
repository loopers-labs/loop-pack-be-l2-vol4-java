package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 선착순 발급 요청 도메인 단위 테스트 (Slice 4) — 신규 요청의 초기 상태·식별자 부여를 검증한다. */
class CouponIssueRequestModelTest {

    @DisplayName("신규 요청은 PENDING 상태로 생성되고, 앱 생성 식별자(TSID)와 요청 시각이 부여된다.")
    @Test
    void newRequest_isPending_withAssignedId() {
        CouponIssueRequestModel request = new CouponIssueRequestModel(100L, 777L);

        assertThat(request.getId()).isNotNull();
        assertThat(request.getUserId()).isEqualTo(100L);
        assertThat(request.getCouponId()).isEqualTo(777L);
        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        assertThat(request.isPending()).isTrue();
        assertThat(request.getRequestedAt()).isNotNull();
        assertThat(request.getProcessedAt()).isNull();
    }

    @DisplayName("생성마다 서로 다른 식별자가 부여된다.")
    @Test
    void eachRequest_getsUniqueId() {
        CouponIssueRequestModel a = new CouponIssueRequestModel(100L, 777L);
        CouponIssueRequestModel b = new CouponIssueRequestModel(100L, 778L);

        assertThat(a.getId()).isNotEqualTo(b.getId());
    }
}
