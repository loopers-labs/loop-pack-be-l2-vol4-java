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

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class CouponRepositoryIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel createCoupon(Integer minOrderAmount) {
        return CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .build();
    }

    @DisplayName("쿠폰 템플릿을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여된다.")
        @Test
        void assignsId() {
            // arrange & act
            CouponModel savedCoupon = couponRepository.save(createCoupon(10_000));

            // assert
            assertThat(savedCoupon.getId()).isNotNull();
        }
    }

    @DisplayName("저장한 쿠폰 템플릿을 재조회할 때,")
    @Nested
    class FindById {

        @DisplayName("이름·할인 타입·할인 값·최소 주문 금액이 그대로 보존된다.")
        @Test
        void preservesFields_whenSavedWithMinOrderAmount() {
            // arrange
            CouponModel savedCoupon = couponRepository.save(createCoupon(10_000));

            // act
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(reloadedCoupon.getName().value()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(reloadedCoupon.getType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(reloadedCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(reloadedCoupon.getMinOrderAmount().value()).isEqualTo(10_000),
                () -> assertThat(reloadedCoupon.getExpiredAt().value()).isNotNull()
            );
        }

        @DisplayName("최소 주문 금액 없이 저장하면 재조회 시에도 최소 주문 금액이 비어 있다.")
        @Test
        void preservesNullMinOrderAmount_whenSavedWithout() {
            // arrange
            CouponModel savedCoupon = couponRepository.save(createCoupon(null));

            // act
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertThat(reloadedCoupon.getMinOrderAmount()).isNull();
        }
    }
}
