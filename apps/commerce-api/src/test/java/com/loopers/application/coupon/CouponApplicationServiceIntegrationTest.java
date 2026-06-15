package com.loopers.application.coupon;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
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

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponApplicationServiceIntegrationTest {

    private static final Long USER_ID = 100L;

    @Autowired
    private CouponApplicationService couponApplicationService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplate saveTemplate(String name, DiscountType type, long value, int validDays) {
        return couponTemplateJpaRepository.save(
                CouponTemplate.create(name, DiscountPolicy.of(type, value), validDays));
    }

    @DisplayName("issue 는 ")
    @Nested
    class Issue {

        @DisplayName("템플릿 혜택을 스냅샷한 AVAILABLE 쿠폰을 INSERT 하고 Issued 를 돌려준다. (AC-19-1)")
        @Test
        void persistsSnapshotCoupon() {
            CouponTemplate template = saveTemplate("신규가입 1만원 할인", DiscountType.FIXED, 10_000L, 30);

            CouponInfo.Issued result = couponApplicationService.issue(USER_ID, template.getId());

            assertThat(result.id()).isNotNull();
            assertThat(result.templateId()).isEqualTo(template.getId());
            assertThat(result.couponName()).isEqualTo("신규가입 1만원 할인");
            assertThat(result.discountType()).isEqualTo(DiscountType.FIXED);
            assertThat(result.discountValue()).isEqualTo(10_000L);
            assertThat(result.status()).isEqualTo(CouponStatus.AVAILABLE);

            UserCoupon persisted = userCouponJpaRepository.findById(result.id()).orElseThrow();
            assertThat(persisted.getUserId()).isEqualTo(USER_ID);
            assertThat(persisted.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("이미 같은 템플릿을 발급받았으면 CONFLICT 를 던진다. (AC-19-2, 1인 1매)")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            couponApplicationService.issue(USER_ID, template.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> couponApplicationService.issue(USER_ID, template.getId()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(userCouponJpaRepository.findAll()).hasSize(1);
        }

        @DisplayName("존재하지 않는 템플릿이면 NOT_FOUND 를 던진다. (AC-19-3)")
        @Test
        void throwsNotFound_whenTemplateMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> couponApplicationService.issue(USER_ID, 99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 템플릿이면 NOT_FOUND 를 던진다. (AC-19-3)")
        @Test
        void throwsNotFound_whenTemplateDeleted() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            couponApplicationService.deleteTemplate(template.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> couponApplicationService.issue(USER_ID, template.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("발급 후 템플릿을 수정해도 이미 발급된 쿠폰의 혜택은 변하지 않는다. (AC-22-2, 스냅샷)")
        @Test
        void issuedCouponIsImmutableToTemplateChange() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 10_000L, 30);
            CouponInfo.Issued issued = couponApplicationService.issue(USER_ID, template.getId());

            couponApplicationService.modifyTemplate(
                    new CouponCriteria.ModifyTemplate(template.getId(), "쿠폰", DiscountType.FIXED, 5_000L, 0L, 30));

            UserCoupon reloaded = userCouponJpaRepository.findById(issued.id()).orElseThrow();
            assertThat(reloaded.getDiscountPolicy().getValue()).isEqualTo(10_000L);
        }
    }

    @DisplayName("getMyCoupons 는 ")
    @Nested
    class GetMyCoupons {

        @DisplayName("본인 쿠폰을 페이징하여 돌려준다.")
        @Test
        void returnsMyCoupons() {
            CouponTemplate t1 = saveTemplate("쿠폰1", DiscountType.FIXED, 1_000L, 30);
            CouponTemplate t2 = saveTemplate("쿠폰2", DiscountType.RATE, 10L, 30);
            couponApplicationService.issue(USER_ID, t1.getId());
            couponApplicationService.issue(USER_ID, t2.getId());

            PageResult<CouponInfo.MyCoupon> result = couponApplicationService.getMyCoupons(USER_ID, 0, 20);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content()).extracting(CouponInfo.MyCoupon::couponName)
                    .containsExactlyInAnyOrder("쿠폰1", "쿠폰2");
        }

        @DisplayName("다른 사용자의 쿠폰은 보이지 않는다.")
        @Test
        void excludesOtherUsersCoupons() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            couponApplicationService.issue(USER_ID, template.getId());
            couponApplicationService.issue(999L, template.getId());

            PageResult<CouponInfo.MyCoupon> result = couponApplicationService.getMyCoupons(USER_ID, 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @DisplayName("만료일이 지난 AVAILABLE 쿠폰은 EXPIRED 로 노출한다. (AC-20-2, 동적 파생)")
        @Test
        void derivesExpiredStatus() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            // 발급 시각을 과거로 두어 만료일이 이미 지난 쿠폰을 만든다.
            UserCoupon expired = UserCoupon.issue(USER_ID, template, ZonedDateTime.now().minusDays(40));
            userCouponJpaRepository.save(expired);

            PageResult<CouponInfo.MyCoupon> result = couponApplicationService.getMyCoupons(USER_ID, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).status()).isEqualTo(CouponStatus.EXPIRED);
        }
    }

    @DisplayName("registerTemplate 는 ")
    @Nested
    class RegisterTemplate {

        @DisplayName("템플릿을 INSERT 하고 id 가 부여된 Template 을 돌려준다.")
        @Test
        void persistsTemplate() {
            CouponInfo.Template result = couponApplicationService.registerTemplate(
                    new CouponCriteria.RegisterTemplate("여름세일 10%", DiscountType.RATE, 10L, 0L, 7));

            assertThat(result.id()).isNotNull();
            assertThat(result.name()).isEqualTo("여름세일 10%");
            assertThat(result.discountType()).isEqualTo(DiscountType.RATE);
            assertThat(result.discountValue()).isEqualTo(10L);
            assertThat(result.validDays()).isEqualTo(7);
            assertThat(couponTemplateJpaRepository.findById(result.id())).isPresent();
        }
    }

    @DisplayName("modifyTemplate 는 ")
    @Nested
    class ModifyTemplate {

        @DisplayName("이름·할인·유효일수를 갱신한다 (dirty checking).")
        @Test
        void updatesTemplate() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);

            couponApplicationService.modifyTemplate(
                    new CouponCriteria.ModifyTemplate(template.getId(), "변경", DiscountType.RATE, 20L, 0L, 14));

            CouponTemplate reloaded = couponTemplateJpaRepository.findById(template.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("변경");
            assertThat(reloaded.getDiscountPolicy().getType()).isEqualTo(DiscountType.RATE);
            assertThat(reloaded.getDiscountPolicy().getValue()).isEqualTo(20L);
            assertThat(reloaded.getValidDays()).isEqualTo(14);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 를 던진다. (AC-22-3)")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class, () ->
                    couponApplicationService.modifyTemplate(
                            new CouponCriteria.ModifyTemplate(99999L, "X", DiscountType.FIXED, 1_000L, 0L, 30)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("deleteTemplate 는 ")
    @Nested
    class DeleteTemplate {

        @DisplayName("템플릿을 논리 삭제하고 목록·조회에서 제외한다. (AC-23-1)")
        @Test
        void softDeletesTemplate() {
            CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);

            couponApplicationService.deleteTemplate(template.getId());

            assertThat(couponTemplateJpaRepository.findById(template.getId()).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(couponApplicationService.getTemplatePage(0, 20).content()).isEmpty();
            CoreException result = assertThrows(CoreException.class,
                    () -> couponApplicationService.getTemplate(template.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 를 던진다.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> couponApplicationService.deleteTemplate(99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getIssueHistory 는 템플릿의 발급 내역(발급자·상태)을 페이징하여 돌려준다. (AC-24-2)")
    @Test
    void getIssueHistory_returnsIssuedCoupons() {
        CouponTemplate template = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
        couponApplicationService.issue(USER_ID, template.getId());
        couponApplicationService.issue(200L, template.getId());

        PageResult<CouponInfo.IssueHistoryItem> result =
                couponApplicationService.getIssueHistory(template.getId(), 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(CouponInfo.IssueHistoryItem::userId)
                .containsExactlyInAnyOrder(USER_ID, 200L);
        assertThat(result.content()).extracting(CouponInfo.IssueHistoryItem::status)
                .containsOnly(CouponStatus.AVAILABLE);
    }
}
