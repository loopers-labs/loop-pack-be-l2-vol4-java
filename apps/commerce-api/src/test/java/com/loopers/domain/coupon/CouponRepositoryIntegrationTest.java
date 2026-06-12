package com.loopers.domain.coupon;

import com.loopers.infrastructure.coupon.CouponJpaEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponRepositoryIntegrationTest {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponRepositoryIntegrationTest(
        CouponRepository couponRepository,
        IssuedCouponRepository issuedCouponRepository,
        CouponJpaRepository couponJpaRepository,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 템플릿을 저장할 때, ")
    @Nested
    class SaveCoupon {
        @DisplayName("도메인 객체가 JPA 엔티티로 저장되고 다시 도메인 객체로 조회된다.")
        @Test
        void savesAndFindsCoupon() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);
            Coupon coupon = new Coupon("신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, expiredAt);

            // act
            Coupon savedCoupon = couponRepository.save(coupon);

            // assert
            CouponJpaEntity savedEntity = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();
            Coupon foundCoupon = couponRepository.find(savedCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedEntity.getName()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(foundCoupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(foundCoupon.getValue()).isEqualTo(10L),
                () -> assertThat(foundCoupon.getMinOrderAmount()).isEqualTo(10_000L)
            );
        }
    }

    @DisplayName("발급 쿠폰을 저장할 때, ")
    @Nested
    class SaveIssuedCoupon {
        @DisplayName("도메인 객체가 JPA 엔티티로 저장되고 다시 도메인 객체로 조회된다.")
        @Test
        void savesAndFindsIssuedCoupon() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, "user1234", expiredAt);

            // act
            IssuedCoupon savedIssuedCoupon = issuedCouponRepository.save(issuedCoupon);

            // assert
            IssuedCouponJpaEntity savedEntity = issuedCouponJpaRepository.findById(savedIssuedCoupon.getId()).orElseThrow();
            IssuedCoupon foundIssuedCoupon = issuedCouponRepository.find(savedIssuedCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedEntity.getCouponId()).isEqualTo(1L),
                () -> assertThat(savedEntity.getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(foundIssuedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(foundIssuedCoupon.getExpiredAt()).isEqualTo(expiredAt)
            );
        }
    }
}
