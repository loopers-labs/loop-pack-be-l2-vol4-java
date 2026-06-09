package com.loopers.domain.coupon;

import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("정상 쿠폰 ID로 요청하면 AVAILABLE 상태로 발급된다.")
        @Test
        void issuesCoupon_whenValidCouponIdIsProvided() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("신규 가입 10% 할인", CouponType.RATE, 10L));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));

            // act
            UserCouponModel result = couponService.issueCoupon(user.getId(), coupon.getId());

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getUserId()).isEqualTo(user.getId()),
                () -> assertThat(result.getCouponId()).isEqualTo(coupon.getId()),
                () -> assertThat(result.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 couponId로 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            Long nonExistentCouponId = 999L;

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(user.getId(), nonExistentCouponId)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("동일 쿠폰을 중복 발급 시도하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponAlreadyIssued() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5,000원 즉시 할인", CouponType.FIXED, 5000L));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            couponService.issueCoupon(user.getId(), coupon.getId());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(user.getId(), coupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
