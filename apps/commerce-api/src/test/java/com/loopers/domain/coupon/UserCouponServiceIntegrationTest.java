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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
@SpringBootTest
public class UserCouponServiceIntegrationTest {

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplateModel template(String name) {
        return couponTemplateService.create(name, CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
            CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("발급 시, 템플릿 스냅샷을 복사한 AVAILABLE 쿠폰을 반환한다.")
        @Test
        void returnsAvailableCoupon_withSnapshot() {
            // arrange
            UUID userId = UUID.randomUUID();
            CouponTemplateModel template = template("발급쿠폰");

            // act
            UserCouponModel issued = userCouponService.issue(userId, template);

            // assert
            assertAll(
                () -> assertThat(issued.getId()).isNotNull(),
                () -> assertThat(issued.getUserId()).isEqualTo(userId),
                () -> assertThat(issued.getTemplateId()).isEqualTo(template.getId()),
                () -> assertThat(issued.getType()).isEqualTo(template.getType()),
                () -> assertThat(issued.getValue()).isEqualTo(template.getValue()),
                () -> assertThat(issued.getMinOrderAmount()).isEqualTo(template.getMinOrderAmount()),
                () -> assertThat(issued.getExpiredAt()).isEqualTo(template.getExpiredAt()),
                () -> assertThat(issued.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("동일 템플릿을 유저가 다시 발급하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // arrange
            UUID userId = UUID.randomUUID();
            CouponTemplateModel template = template("발급쿠폰");
            userCouponService.issue(userId, template);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                userCouponService.issue(userId, template)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("다른 유저는 동일 템플릿을 각각 발급받을 수 있다.")
        @Test
        void allowsDifferentUsers_forSameTemplate() {
            // arrange
            CouponTemplateModel template = template("발급쿠폰");

            // act
            UserCouponModel a = userCouponService.issue(UUID.randomUUID(), template);
            UserCouponModel b = userCouponService.issue(UUID.randomUUID(), template);

            // assert
            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때,")
    @Nested
    class GetMyCoupons {

        @DisplayName("본인이 발급받은 쿠폰만 반환한다.")
        @Test
        void returnsOnlyOwnCoupons() {
            // arrange
            UUID userId = UUID.randomUUID();
            userCouponService.issue(userId, template("쿠폰A"));
            userCouponService.issue(userId, template("쿠폰B"));
            userCouponService.issue(UUID.randomUUID(), template("쿠폰C")); // 타 유저

            // act
            List<UserCouponModel> coupons = userCouponService.getMyCoupons(userId);

            // assert
            assertThat(coupons).hasSize(2);
            assertThat(coupons).allMatch(c -> c.getUserId().equals(userId));
        }
    }

    @DisplayName("템플릿별 발급 내역을 조회할 때,")
    @Nested
    class GetIssuesByTemplate {

        @DisplayName("해당 템플릿으로 발급된 쿠폰을 페이징해 반환한다.")
        @Test
        void returnsPagedIssues() {
            // arrange
            CouponTemplateModel template = template("인기쿠폰");
            userCouponService.issue(UUID.randomUUID(), template);
            userCouponService.issue(UUID.randomUUID(), template);
            userCouponService.issue(UUID.randomUUID(), template);

            // act
            Page<UserCouponModel> page = userCouponService.getIssuesByTemplate(template.getId(), PageRequest.of(0, 2));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).hasSize(2)
            );
        }
    }
}
