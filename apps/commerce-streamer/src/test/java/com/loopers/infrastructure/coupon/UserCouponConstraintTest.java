package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserCouponConstraintTest {

    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserCouponConstraintTest(UserCouponJpaRepository userCouponJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 유저에게 같은 쿠폰 정책을 두 번 발급하려 하면 DB unique 제약이 막는다 — 중복 발급 최종 방어선.")
    @Test
    void rejectsDuplicateUserCoupon_atDatabaseLevel() {
        // given : (userId, couponPolicyId) 로 이미 1건 발급됨
        long userId = 1L;
        long policyId = 10L;
        userCouponJpaRepository.saveAndFlush(newCoupon(userId, policyId));

        // when / then : 같은 조합을 또 저장하면 unique 제약 위반
        assertThatThrownBy(() -> userCouponJpaRepository.saveAndFlush(newCoupon(userId, policyId)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserCoupon newCoupon(long userId, long policyId) {
        return UserCoupon.issue(userId, policyId, "FIXED", 1000L, null, ZonedDateTime.now().plusDays(7));
    }
}
