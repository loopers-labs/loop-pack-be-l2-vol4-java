package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.CouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
import com.loopers.coupon.interfaces.CouponIssueRequestedMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssuanceServiceTest {

    private final CouponIssuanceService couponIssuanceService;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponIssuanceServiceTest(CouponIssuanceService couponIssuanceService,
                              CouponJpaRepository couponJpaRepository,
                              UserCouponJpaRepository userCouponJpaRepository,
                              CouponIssueRequestJpaRepository couponIssueRequestJpaRepository,
                              DatabaseCleanUp databaseCleanUp) {
        this.couponIssuanceService = couponIssuanceService;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.couponIssueRequestJpaRepository = couponIssueRequestJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long seedCoupon(Long quantity) {
        Coupon coupon = couponJpaRepository.save(
                Coupon.createLimited("선착순", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30), quantity));
        return coupon.getId();
    }

    private CouponIssueRequestedMessage seedRequest(Long couponId, Long userId) {
        String requestId = UUID.randomUUID().toString();
        couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(requestId, couponId, userId));
        return new CouponIssueRequestedMessage(requestId, couponId, userId, ZonedDateTime.now());
    }

    private CouponIssueRequestStatus statusOf(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("발급 수량 한도까지만 발급되고, 초과 요청은 거절된다")
    void givenLimitedCoupon_whenMoreRequestsThanQuantity_thenIssuesUpToLimit() {
        Long couponId = seedCoupon(2L);
        CouponIssueRequestedMessage first = seedRequest(couponId, 1L);
        CouponIssueRequestedMessage second = seedRequest(couponId, 2L);
        CouponIssueRequestedMessage over = seedRequest(couponId, 3L);

        couponIssuanceService.issue(first);
        couponIssuanceService.issue(second);
        couponIssuanceService.issue(over);

        assertThat(userCouponJpaRepository.countByCouponId(couponId)).isEqualTo(2);
        assertThat(statusOf(first.requestId())).isEqualTo(CouponIssueRequestStatus.SUCCESS);
        assertThat(statusOf(over.requestId())).isEqualTo(CouponIssueRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("같은 requestId 를 두 번 처리해도 한 번만 발급한다(멱등)")
    void givenSameRequestId_whenIssuedTwice_thenIssuedOnce() {
        Long couponId = seedCoupon(10L);
        CouponIssueRequestedMessage message = seedRequest(couponId, 1L);

        couponIssuanceService.issue(message);
        couponIssuanceService.issue(message);

        assertThat(userCouponJpaRepository.countByCouponId(couponId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자가 다른 요청으로 두 번 신청하면 한 번만 발급된다(중복 발급 방지)")
    void givenSameUserTwoRequests_whenIssued_thenIssuedOnce() {
        Long couponId = seedCoupon(10L);

        couponIssuanceService.issue(seedRequest(couponId, 1L));
        CouponIssueRequestedMessage dup = seedRequest(couponId, 1L);
        couponIssuanceService.issue(dup);

        assertThat(userCouponJpaRepository.countByCouponId(couponId)).isEqualTo(1);
        assertThat(statusOf(dup.requestId())).isEqualTo(CouponIssueRequestStatus.REJECTED);
    }
}
