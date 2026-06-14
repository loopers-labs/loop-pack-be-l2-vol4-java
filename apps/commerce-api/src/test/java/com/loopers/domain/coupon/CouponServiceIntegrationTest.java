package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.money.Money;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Coupon saveTemplate() {
        return couponJpaRepository.save(new Coupon(
            "10% 할인", new Discount(CouponType.RATE, 10L),
            new Money(BigDecimal.valueOf(10000)), EXPIRED_AT
        ));
    }

    @DisplayName("쿠폰 템플릿을 등록할 때, ")
    @Nested
    class Register {
        @DisplayName("입력한 값으로 템플릿이 저장된다.")
        @Test
        void persistsTemplate() {
            // act
            Coupon coupon = couponService.register(
                "신규 5000원 할인", new Discount(CouponType.FIXED, 5000L),
                new Money(BigDecimal.valueOf(20000)), EXPIRED_AT
            );

            // assert
            Coupon persisted = couponJpaRepository.findById(coupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(persisted.getName()).isEqualTo("신규 5000원 할인"),
                () -> assertThat(persisted.getDiscount().getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(persisted.getDiscount().getValue()).isEqualTo(5000L),
                () -> assertThat(persisted.getMinOrderAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                () -> assertThat(persisted.getExpiredAt()).isEqualTo(EXPIRED_AT)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때, ")
    @Nested
    class Update {
        @DisplayName("변경된 값이 반영된다.")
        @Test
        void updatesTemplate() {
            // arrange
            Coupon template = saveTemplate();

            // act
            couponService.update(template.getId(), "변경된 할인",
                new Discount(CouponType.FIXED, 3000L), new Money(BigDecimal.valueOf(5000)), EXPIRED_AT);

            // assert
            Coupon reloaded = couponService.getCoupon(template.getId());
            assertAll(
                () -> assertThat(reloaded.getName()).isEqualTo("변경된 할인"),
                () -> assertThat(reloaded.getDiscount().getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(reloaded.getDiscount().getValue()).isEqualTo(3000L)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 상세 조회할 때, ")
    @Nested
    class GetCoupon {
        @DisplayName("존재하지 않는 템플릿이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> couponService.getCoupon(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때, ")
    @Nested
    class GetCouponPage {
        @DisplayName("활성 템플릿만 페이지로 반환되고, 삭제된 템플릿은 제외된다.")
        @Test
        void returnsActiveTemplatesOnly() {
            // arrange
            saveTemplate();
            Coupon deleted = saveTemplate();
            couponService.delete(deleted.getId());

            // act
            List<Coupon> page = couponService.getCouponPage(0, 20);
            long count = couponService.countCoupons();

            // assert
            assertAll(
                () -> assertThat(page).hasSize(1),
                () -> assertThat(count).isEqualTo(1)
            );
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {
        @DisplayName("존재하는 템플릿이면, AVAILABLE 상태의 UserCoupon이 저장된다.")
        @Test
        void persistsUserCoupon_whenTemplateExists() {
            // arrange
            Coupon template = saveTemplate();
            Long userId = 1L;

            // act
            UserCoupon issued = couponService.issue(userId, template.getId());

            // assert
            UserCoupon persisted = userCouponJpaRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                () -> assertThat(persisted.getUserId()).isEqualTo(userId),
                () -> assertThat(persisted.getCouponId()).isEqualTo(template.getId()),
                () -> assertThat(persisted.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 템플릿이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> couponService.issue(1L, 999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 쿠폰을 조회할 때, ")
    @Nested
    class GetMyCoupons {
        @DisplayName("발급받은 쿠폰이 템플릿 정보와 AVAILABLE 상태로 반환된다.")
        @Test
        void returnsIssuedCouponsWithTemplateInfo() {
            // arrange
            Coupon template = saveTemplate();
            Long userId = 1L;
            couponService.issue(userId, template.getId());

            // act
            List<UserCouponInfo> result = couponFacade.getMyCoupons(userId);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).couponId()).isEqualTo(template.getId()),
                () -> assertThat(result.get(0).name()).isEqualTo("10% 할인"),
                () -> assertThat(result.get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }

    @DisplayName("발급 내역을 조회할 때, ")
    @Nested
    class GetIssues {
        @DisplayName("특정 템플릿의 발급 내역이 페이지로 반환된다.")
        @Test
        void returnsIssuesForTemplate() {
            // arrange
            Coupon template = saveTemplate();
            couponService.issue(1L, template.getId());
            couponService.issue(2L, template.getId());

            // act
            List<UserCoupon> issues = couponService.getIssues(template.getId(), 0, 20);
            long count = couponService.countIssues(template.getId());

            // assert
            assertAll(
                () -> assertThat(issues).hasSize(2),
                () -> assertThat(count).isEqualTo(2),
                () -> assertThat(issues).extracting(UserCoupon::getCouponId).containsOnly(template.getId())
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때, ")
    @Nested
    class Delete {
        @DisplayName("삭제된 템플릿은 상세 조회 시 NOT_FOUND 이지만, 이미 발급된 쿠폰은 계속 조회된다.")
        @Test
        void deletedTemplateIsHidden_butIssuedCouponStillResolves() {
            // arrange
            Coupon template = saveTemplate();
            Long userId = 1L;
            couponService.issue(userId, template.getId());

            // act
            couponService.delete(template.getId());

            // assert
            CoreException notFound = assertThrows(CoreException.class, () -> couponService.getCoupon(template.getId()));
            List<UserCouponInfo> myCoupons = couponFacade.getMyCoupons(userId);
            assertAll(
                () -> assertThat(notFound.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(myCoupons).hasSize(1),
                () -> assertThat(myCoupons.get(0).couponId()).isEqualTo(template.getId())
            );
        }
    }
}
