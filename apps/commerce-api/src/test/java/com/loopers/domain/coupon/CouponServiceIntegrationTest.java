package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private LocalDateTime future() {
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    @DisplayName("쿠폰 템플릿을 등록한 다음 조회할 때")
    @Nested
    class CreateAndGet {

        @DisplayName("등록된 동일한 쿠폰을 조회할 수 있다.")
        @Test
        void roundTrip() {
            CouponModel created = couponService.createCoupon("신규가입 10% 할인", CouponType.RATE, 10L, 10000L, future(), null);

            CouponModel found = couponService.getCoupon(created.getId());

            assertAll(
                    () -> assertThat(found.getId()).isEqualTo(created.getId()),
                    () -> assertThat(found.getName()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(found.getDiscountPolicy().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(found.getDiscountPolicy().value()).isEqualTo(10L),
                    () -> assertThat(found.getMinOrderAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> couponService.getCoupon(99L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때")
    @Nested
    class GetCoupons {

        @DisplayName("등록된 쿠폰들이 페이지로 조회된다.")
        @Test
        void returnsPage() {
            couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);
            couponService.createCoupon("B", CouponType.RATE, 20L, null, future(), null);

            assertThat(couponService.getCoupons(PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때")
    @Nested
    class UpdateCoupon {

        @DisplayName("이름·할인 정책이 갱신된다.")
        @Test
        void updates() {
            CouponModel created = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);

            couponService.updateCoupon(created.getId(), "B", CouponType.RATE, 15L, 5000L, future());

            CouponModel found = couponService.getCoupon(created.getId());
            assertAll(
                    () -> assertThat(found.getName()).isEqualTo("B"),
                    () -> assertThat(found.getDiscountPolicy().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(found.getDiscountPolicy().value()).isEqualTo(15L),
                    () -> assertThat(found.getMinOrderAmount()).isEqualTo(5000L)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때")
    @Nested
    class DeleteCoupon {

        @DisplayName("soft delete되어 조회되지 않는다.")
        @Test
        void softDeletes() {
            CouponModel created = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);

            couponService.deleteCoupon(created.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> couponService.getCoupon(created.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}