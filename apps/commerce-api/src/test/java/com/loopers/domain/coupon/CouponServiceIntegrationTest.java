package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.infrastructure.coupon.IssuedCouponJpaEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    private final CouponFacade couponFacade;
    private final CouponService couponService;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponServiceIntegrationTest(
        CouponFacade couponFacade,
        CouponService couponService,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponFacade = couponFacade;
        this.couponService = couponService;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class IssueCoupon {
        @DisplayName("유효한 쿠폰이면, 회원에게 쿠폰을 발급한다.")
        @Test
        void issuesCoupon_whenCouponIsAvailable() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );

            // act
            CouponInfo.Issued issuedCoupon = couponFacade.issueCoupon(coupon.id(), "user1234", now);

            // assert
            assertAll(
                () -> assertThat(issuedCoupon.couponId()).isEqualTo(coupon.id()),
                () -> assertThat(issuedCoupon.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(issuedCoupon.status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("같은 쿠폰을 같은 회원에게 중복 발급하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponIsAlreadyIssuedToUser() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );
            couponFacade.issueCoupon(coupon.id(), "user1234", now);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                couponFacade.issueCoupon(coupon.id(), "user1234", now);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("발급 쿠폰을 조회할 때, ")
    @Nested
    class GetIssuedCoupons {
        @DisplayName("현재 시각 기준 상태를 포함해 내 쿠폰 목록을 반환한다.")
        @Test
        void getsMyCouponsWithCurrentStatus() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );
            couponFacade.issueCoupon(coupon.id(), "user1234", now);

            // act
            List<CouponInfo.Issued> issuedCoupons = couponFacade.getMyCoupons("user1234", now);

            // assert
            assertAll(
                () -> assertThat(issuedCoupons).hasSize(1),
                () -> assertThat(issuedCoupons.get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }

    @DisplayName("발급 쿠폰을 사용할 때, ")
    @Nested
    class UseIssuedCoupon {
        @DisplayName("사용 가능한 쿠폰이면, 할인 금액을 반환하고 USED 상태로 변경한다.")
        @Test
        void usesIssuedCoupon_whenCouponIsAvailable() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );
            CouponInfo.Issued issuedCoupon = couponFacade.issueCoupon(coupon.id(), "user1234", now);

            // act
            CouponUseResult result = couponService.useIssuedCoupon("user1234", issuedCoupon.id(), 20_000L, now);

            // assert
            IssuedCouponJpaEntity savedIssuedCoupon = issuedCouponJpaRepository.findById(issuedCoupon.id()).orElseThrow();
            assertAll(
                () -> assertThat(result.discountAmount()).isEqualTo(2_000L),
                () -> assertThat(savedIssuedCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(savedIssuedCoupon.getUsedAt()).isEqualTo(now)
            );
        }

        @DisplayName("사용 실패 시, 쿠폰 상태를 변경하지 않는다.")
        @Test
        void doesNotUseIssuedCoupon_whenUserIsNotOwner() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );
            CouponInfo.Issued issuedCoupon = couponFacade.issueCoupon(coupon.id(), "user1234", now);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                couponService.useIssuedCoupon("user5678", issuedCoupon.id(), 20_000L, now);
            });

            // assert
            IssuedCouponJpaEntity savedIssuedCoupon = issuedCouponJpaRepository.findById(issuedCoupon.id()).orElseThrow();
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(savedIssuedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(savedIssuedCoupon.getUsedAt()).isNull()
            );
        }
    }
}
