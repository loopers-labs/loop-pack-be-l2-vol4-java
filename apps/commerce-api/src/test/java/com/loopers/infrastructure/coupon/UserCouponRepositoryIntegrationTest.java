package com.loopers.infrastructure.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserCouponRepositoryIntegrationTest {

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel coupon(Long couponId, Integer minOrderAmount) {
        CouponModel coupon = CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build();
        ReflectionTestUtils.setField(coupon, "id", couponId);

        return coupon;
    }

    @DisplayName("발급 쿠폰을 저장한 뒤 재조회할 때,")
    @Nested
    class SaveAndFind {

        @DisplayName("스냅샷 값이 그대로 보존되고 식별자가 부여된다.")
        @Test
        void preservesSnapshot_andAssignsId() {
            // arrange
            UserCouponModel issuedCoupon = UserCouponModel.issue(100L, coupon(1L, 10_000));

            // act
            UserCouponModel savedCoupon = userCouponRepository.save(issuedCoupon);
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(reloadedCoupon.getId()).isNotNull(),
                () -> assertThat(reloadedCoupon.getUserId()).isEqualTo(100L),
                () -> assertThat(reloadedCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(reloadedCoupon.getName()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(reloadedCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(reloadedCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(reloadedCoupon.getMinOrderAmount()).isEqualTo(10_000),
                () -> assertThat(reloadedCoupon.getExpiredAt()).isNotNull(),
                () -> assertThat(reloadedCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("최소 주문 금액이 0인 템플릿에서 발급하면 0이 그대로 보존된다.")
        @Test
        void preservesZeroMinOrderAmount() {
            // arrange
            UserCouponModel issuedCoupon = UserCouponModel.issue(100L, coupon(1L, null));

            // act
            UserCouponModel savedCoupon = userCouponRepository.save(issuedCoupon);
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertThat(reloadedCoupon.getMinOrderAmount()).isZero();
        }
    }

    @DisplayName("회원·템플릿 발급 이력을 확인할 때,")
    @Nested
    class ExistsByUserIdAndCouponId {

        @DisplayName("같은 회원이 같은 템플릿에서 발급받은 이력이 있으면 true를 반환한다.")
        @Test
        void returnsTrue_whenAlreadyIssued() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertThat(userCouponRepository.existsByUserIdAndCouponId(100L, 1L)).isTrue();
        }

        @DisplayName("발급 이력이 없으면 false를 반환한다(다른 회원·다른 템플릿 포함).")
        @Test
        void returnsFalse_whenNotIssued() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertAll(
                () -> assertThat(userCouponRepository.existsByUserIdAndCouponId(200L, 1L)).isFalse(),
                () -> assertThat(userCouponRepository.existsByUserIdAndCouponId(100L, 2L)).isFalse()
            );
        }
    }
}
