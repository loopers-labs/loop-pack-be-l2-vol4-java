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

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CouponService 통합 — H2에서 발급 영속 상태와 1유저 1템플릿 제약을 검증한다.
 * 단위(CouponServiceTest)는 분기, 통합은 실제 저장/조회를 본다.
 */
@SpringBootTest
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private Long templateId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long savedTemplateId() {
        CouponTemplate template = couponTemplateRepository.save(
            new CouponTemplate("정액 3천원", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(7)));
        return template.getId();
    }

    @DisplayName("쿠폰 발급 시")
    @Nested
    class Issue {

        @DisplayName("발급하면 AVAILABLE 상태의 쿠폰이 DB에 저장된다")
        @Test
        void persistsAvailableCoupon() {
            // given
            templateId = savedTemplateId();

            // when
            IssuedCoupon issued = couponService.issue(USER_ID, templateId);

            // then
            assertAll(
                () -> assertThat(issued.getId()).isNotNull(),
                () -> assertThat(issued.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(issued.getUserId()).isEqualTo(USER_ID)
            );
        }

        @DisplayName("같은 유저가 같은 템플릿을 두 번 발급하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenIssuedTwice() {
            // given
            templateId = savedTemplateId();
            couponService.issue(USER_ID, templateId);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(USER_ID, templateId));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 템플릿으로 발급하면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenTemplateMissing() {
            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(USER_ID, 9_999L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
