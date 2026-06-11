package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponFacadeTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("존재하지 않는 템플릿 ID로 쿠폰 발급을 시도하면 NOT_FOUND 예외가 발생한다.")
    void issueCoupon_TemplateNotFound_ShouldThrowException() {
        // given
        Long userId = 1L;
        Long invalidTemplateId = 999L;

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
            couponFacade.issueCoupon(userId, invalidTemplateId)
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("이미 쿠폰을 발급받은 사용자가 다시 동일한 쿠폰을 발급받으려고 하면 CONFLICT 예외가 발생한다.")
    void issueCoupon_AlreadyIssued_ShouldThrowException() {
        // given
        Long userId = 1L;
        CouponTemplate template = couponRepository.saveTemplate(
            new CouponTemplate("10% 할인 쿠폰", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        );

        // 첫 번째 발급은 성공해야 함
        couponFacade.issueCoupon(userId, template.getId());

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
            couponFacade.issueCoupon(userId, template.getId())
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("정상적인 발급 요청 시 쿠폰 발급이 성공하고 저장된다.")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        CouponTemplate template = couponRepository.saveTemplate(
            new CouponTemplate("5000원 할인 쿠폰", CouponType.FIXED, new BigDecimal("5000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        );

        // when
        CouponIssue issue = couponFacade.issueCoupon(userId, template.getId());

        // then
        assertThat(issue).isNotNull();
        assertThat(issue.getUserId()).isEqualTo(userId);
        assertThat(issue.getCouponTemplateId()).isEqualTo(template.getId());

        // DB에 실제로 저장되었는지 검증
        CouponIssue saved = couponRepository.findIssueById(issue.getId()).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCouponTemplateId()).isEqualTo(template.getId());
    }
}
