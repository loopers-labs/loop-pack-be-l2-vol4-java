package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponPolicyRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponFacadeIntegrationTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");

    private final CouponFacade couponFacade;
    private final CouponService couponService;
    private final CouponPolicyRepository couponPolicyRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponFacadeIntegrationTest(
        CouponFacade couponFacade,
        CouponService couponService,
        CouponPolicyRepository couponPolicyRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponFacade = couponFacade;
        this.couponService = couponService;
        this.couponPolicyRepository = couponPolicyRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponPolicy savedPolicy() {
        return couponPolicyRepository.save(new CouponPolicy("3천원 할인", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT));
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("발급하면, AVAILABLE 표시 상태의 CouponInfo 를 반환하고 정책 스냅샷이 그대로 담긴다.")
        @Test
        void returnsAvailableCouponInfo_whenIssued() {
            // given
            Long userId = 1L;
            CouponPolicy policy = savedPolicy();

            // when
            CouponInfo info = couponFacade.issue(userId, policy.getId());

            // then
            assertAll(
                () -> assertThat(info.id()).isNotNull(),
                () -> assertThat(info.couponPolicyId()).isEqualTo(policy.getId()),
                () -> assertThat(info.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(info.discountValue()).isEqualTo(3_000L),
                () -> assertThat(info.minOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(info.expiredAt()).isEqualTo(EXPIRED_AT),
                () -> assertThat(info.status()).isEqualTo(CouponDisplayStatus.AVAILABLE),
                () -> assertThat(info.usedAt()).isNull()
            );
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때, ")
    @Nested
    class GetMyCoupons {

        @DisplayName("해당 사용자의 사용자 쿠폰만 반환하며, 사용한 쿠폰은 USED, 사용 전 쿠폰은 AVAILABLE 로 표시된다.")
        @Test
        void returnsOwnCouponsWithDisplayStatus() {
            // given
            Long ownerId = 1L;
            Long otherUserId = 2L;
            CouponPolicy policy = savedPolicy();
            CouponInfo available = couponFacade.issue(ownerId, policy.getId());
            CouponInfo used = couponFacade.issue(ownerId, policy.getId());
            couponService.use(ownerId, used.id(), 10_000L);
            couponFacade.issue(otherUserId, policy.getId());

            // when
            List<CouponInfo> found = couponFacade.getMyCoupons(ownerId);

            // then
            assertThat(found)
                .extracting(CouponInfo::id, CouponInfo::status)
                .containsExactlyInAnyOrder(
                    tuple(available.id(), CouponDisplayStatus.AVAILABLE),
                    tuple(used.id(), CouponDisplayStatus.USED)
                );
        }
    }
}
