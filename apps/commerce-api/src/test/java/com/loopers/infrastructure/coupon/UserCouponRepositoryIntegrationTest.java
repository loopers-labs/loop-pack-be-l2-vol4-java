package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponPolicyRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserCouponRepositoryIntegrationTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-06T00:00:00+09:00");

    private final UserCouponRepository userCouponRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserCouponRepositoryIntegrationTest(
        UserCouponRepository userCouponRepository,
        CouponPolicyRepository couponPolicyRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userCouponRepository = userCouponRepository;
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

    @DisplayName("사용자 쿠폰을 저장하고 단건 조회할 때, ")
    @Nested
    class SaveAndFindById {

        @DisplayName("저장한 사용자 쿠폰을 id 로 조회하면, 저장한 값이 그대로 반환된다.")
        @Test
        void returnsSavedUserCoupon_whenFindById() {
            // given
            CouponPolicy policy = savedPolicy();
            UserCoupon saved = userCouponRepository.save(UserCoupon.issue(1L, policy, NOW));

            // when
            Optional<UserCoupon> found = userCouponRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertAll(
                () -> assertThat(found.get().getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.get().getUserId()).isEqualTo(1L),
                () -> assertThat(found.get().getCouponPolicyId()).isEqualTo(policy.getId()),
                () -> assertThat(found.get().getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(found.get().getUsedAt()).isNull()
            );
        }
    }

    @DisplayName("사용자별 사용자 쿠폰을 조회할 때, ")
    @Nested
    class FindByUserId {

        @DisplayName("해당 사용자의 사용자 쿠폰만 반환한다.")
        @Test
        void returnsOnlyOwnUserCoupons() {
            // given
            CouponPolicy policy = savedPolicy();
            Long ownerId = 1L;
            Long otherUserId = 2L;
            UserCoupon mine1 = userCouponRepository.save(UserCoupon.issue(ownerId, policy, NOW));
            UserCoupon mine2 = userCouponRepository.save(UserCoupon.issue(ownerId, policy, NOW));
            userCouponRepository.save(UserCoupon.issue(otherUserId, policy, NOW));

            // when
            List<UserCoupon> found = userCouponRepository.findByUserId(ownerId);

            // then
            assertThat(found).extracting(UserCoupon::getId)
                .containsExactlyInAnyOrder(mine1.getId(), mine2.getId());
        }
    }

    @DisplayName("정책별 발급 내역을 페이징 조회할 때, ")
    @Nested
    class FindByCouponPolicyId {

        @DisplayName("해당 정책의 사용자 쿠폰을 페이지 크기만큼 잘라서 전체 개수와 함께 반환한다.")
        @Test
        void returnsPagedIssuesOfPolicy_withTotalCount() {
            // given
            CouponPolicy policy = savedPolicy();
            CouponPolicy otherPolicy = savedPolicy();
            userCouponRepository.save(UserCoupon.issue(1L, policy, NOW));
            userCouponRepository.save(UserCoupon.issue(2L, policy, NOW));
            userCouponRepository.save(UserCoupon.issue(3L, policy, NOW));
            userCouponRepository.save(UserCoupon.issue(4L, otherPolicy, NOW));

            // when
            Page<UserCoupon> page = userCouponRepository.findByCouponPolicyId(policy.getId(), PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getTotalElements()).isEqualTo(3L)
            );
        }
    }
}
