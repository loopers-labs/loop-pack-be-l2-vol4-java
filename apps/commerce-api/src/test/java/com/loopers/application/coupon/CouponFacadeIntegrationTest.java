package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponPolicyRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @DisplayName("어드민이 쿠폰 정책을 생성할 때, ")
    @Nested
    class CreatePolicy {

        @DisplayName("생성하면, 입력값과 생성 일시가 담긴 CouponAdminInfo 를 반환하고 삭제 일시는 null 이다.")
        @Test
        void returnsAdminInfo_whenCreated() {
            // when
            CouponAdminInfo info = couponFacade.createPolicy("신규 쿠폰", CouponType.RATE, 10L, 20_000L, EXPIRED_AT);

            // then
            assertAll(
                () -> assertThat(info.id()).isNotNull(),
                () -> assertThat(info.name()).isEqualTo("신규 쿠폰"),
                () -> assertThat(info.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(info.value()).isEqualTo(10L),
                () -> assertThat(info.minOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(info.expiredAt()).isEqualTo(EXPIRED_AT),
                () -> assertThat(info.createdAt()).isNotNull(),
                () -> assertThat(info.deletedAt()).isNull()
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책 목록을 조회할 때, ")
    @Nested
    class GetPolicies {

        @DisplayName("삭제된 정책을 포함해 최신순(id 내림차순)으로 페이지 단위 반환한다.")
        @Test
        void returnsPoliciesIncludingDeleted_inLatestOrder() {
            // given
            CouponAdminInfo first = couponFacade.createPolicy("쿠폰1", CouponType.FIXED, 1_000L, null, EXPIRED_AT);
            CouponAdminInfo second = couponFacade.createPolicy("쿠폰2", CouponType.FIXED, 2_000L, null, EXPIRED_AT);
            CouponAdminInfo third = couponFacade.createPolicy("쿠폰3", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(second.id());

            // when
            Page<CouponAdminInfo> page = couponFacade.getPolicies(0, 10);

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3L),
                () -> assertThat(page.getContent()).extracting(CouponAdminInfo::id)
                    .containsExactly(third.id(), second.id(), first.id())
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 단건 조회할 때, ")
    @Nested
    class GetPolicy {

        @DisplayName("삭제된 정책도 조회되며, deletedAt 이 채워져 반환된다.")
        @Test
        void returnsDeletedPolicyWithDeletedAt() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("삭제될 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(created.id());

            // when
            CouponAdminInfo found = couponFacade.getPolicy(created.id());

            // then
            assertAll(
                () -> assertThat(found.id()).isEqualTo(created.id()),
                () -> assertThat(found.deletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 수정할 때, ")
    @Nested
    class UpdatePolicy {

        @DisplayName("메타 정보를 수정하면, 갱신된 값이 담긴 CouponAdminInfo 를 반환하고 타입·할인값은 유지된다.")
        @Test
        void returnsUpdatedInfo_whenMetaUpdated() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);
            ZonedDateTime newExpiredAt = ZonedDateTime.parse("2100-01-31T23:59:59+09:00");

            // when
            CouponAdminInfo updated = couponFacade.updatePolicy(created.id(), "변경된 쿠폰", 20_000L, newExpiredAt);

            // then
            assertAll(
                () -> assertThat(updated.name()).isEqualTo("변경된 쿠폰"),
                () -> assertThat(updated.minOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(updated.expiredAt()).isEqualTo(newExpiredAt),
                () -> assertThat(updated.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(updated.value()).isEqualTo(3_000L)
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 삭제할 때, ")
    @Nested
    class DeletePolicy {

        @DisplayName("삭제하면 soft-delete 되어, 이후 해당 정책으로의 신규 발급은 COUPON_POLICY_NOT_FOUND 로 차단된다.")
        @Test
        void blocksIssueAfterSoftDelete() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("삭제될 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(created.id());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponFacade.issue(1L, created.id()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND);
        }
    }

    @DisplayName("어드민이 정책별 발급 내역을 조회할 때, ")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("해당 정책으로 발급된 사용자 쿠폰을 사용자 ID·표시 상태와 함께 페이지 단위로 반환한다.")
        @Test
        void returnsIssuedCouponsWithUserIdAndStatus() {
            // given
            CouponPolicy policy = savedPolicy();
            CouponInfo availableForUser1 = couponFacade.issue(1L, policy.getId());
            CouponInfo usedForUser2 = couponFacade.issue(2L, policy.getId());
            couponService.use(2L, usedForUser2.id(), 10_000L);

            // when
            Page<IssuedCouponInfo> page = couponFacade.getIssuedCoupons(policy.getId(), 0, 10);

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(2L),
                () -> assertThat(page.getContent())
                    .extracting(IssuedCouponInfo::id, IssuedCouponInfo::userId, IssuedCouponInfo::status)
                    .containsExactlyInAnyOrder(
                        tuple(availableForUser1.id(), 1L, CouponDisplayStatus.AVAILABLE),
                        tuple(usedForUser2.id(), 2L, CouponDisplayStatus.USED)
                    )
            );
        }
    }
}
