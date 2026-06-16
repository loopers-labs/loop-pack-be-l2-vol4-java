package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserCouponRepositoryTest {

    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel saveCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "테스트 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(ZonedDateTime.now().plusDays(30))
        ));
    }

    @DisplayName("동일한 유저와 쿠폰으로 중복 저장하면, DataIntegrityViolationException 이 발생한다.")
    @Test
    void throwsException_whenDuplicateUserCouponIsInserted() {
        CouponModel coupon = saveCoupon();
        userCouponRepository.save(new UserCouponModel(USER_ID, coupon));

        assertThrows(DataIntegrityViolationException.class, () ->
                userCouponRepository.save(new UserCouponModel(USER_ID, coupon))
        );
    }
}
