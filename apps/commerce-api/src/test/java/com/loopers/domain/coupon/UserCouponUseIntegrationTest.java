package com.loopers.domain.coupon;

import com.loopers.fixture.CouponTemplateFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class UserCouponUseIntegrationTest {

    @Autowired private UserCouponService userCouponService;
    @Autowired private CouponTemplateService couponTemplateService;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserCouponModel issue(UUID userId, String name) {
        CouponTemplateModel template = couponTemplateService.create(name, CouponTemplateFixture.TYPE,
            CouponTemplateFixture.VALUE, CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
        return userCouponService.issue(userId, template);
    }

    @DisplayName("보유 쿠폰을 조회할 때,")
    @Nested
    class GetOwned {

        @DisplayName("타 유저 소유 쿠폰을 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwned() {
            UserCouponModel coupon = issue(UUID.randomUUID(), "쿠폰");

            CoreException ex = assertThrows(CoreException.class, () ->
                userCouponService.getOwned(coupon.getId(), UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 쿠폰 사용 시, USED 로 전이되고 orderId 가 기록된다.")
        @Test
        void marksUsed_whenAvailable() {
            UUID userId = UUID.randomUUID();
            UserCouponModel coupon = issue(userId, "쿠폰");
            UUID orderId = UUID.randomUUID();

            userCouponService.use(coupon.getId(), orderId);

            UserCouponModel reloaded = userCouponRepository.find(coupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(reloaded.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(reloaded.getOrderId()).isEqualTo(orderId)
            );
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면, CONFLICT 예외가 발생한다. (조건부 UPDATE affected=0)")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            UUID userId = UUID.randomUUID();
            UserCouponModel coupon = issue(userId, "쿠폰");
            userCouponService.use(coupon.getId(), UUID.randomUUID());

            CoreException ex = assertThrows(CoreException.class, () ->
                userCouponService.use(coupon.getId(), UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰을 복구(release)할 때,")
    @Nested
    class Release {

        @DisplayName("releaseByOrderId 후, AVAILABLE 로 복구되고 재사용 가능하다.")
        @Test
        void restoresAndReusable() {
            UUID userId = UUID.randomUUID();
            UserCouponModel coupon = issue(userId, "쿠폰");
            UUID orderId = UUID.randomUUID();
            userCouponService.use(coupon.getId(), orderId);

            userCouponService.releaseByOrderId(orderId);

            UserCouponModel reloaded = userCouponRepository.find(coupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(reloaded.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(reloaded.getOrderId()).isNull()
            );
            // 복구 후 재사용 가능
            userCouponService.use(coupon.getId(), UUID.randomUUID());
        }
    }
}
