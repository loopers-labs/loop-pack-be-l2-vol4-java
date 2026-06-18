package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.application.coupon.CouponRepository;
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
    @DisplayName("議댁옱?섏? ?딅뒗 ?쒗뵆由?ID濡?荑좏룿 諛쒓툒???쒕룄?섎㈃ NOT_FOUND ?덉쇅媛 諛쒖깮?쒕떎.")
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
    @DisplayName("?대? 荑좏룿??諛쒓툒諛쏆? ?ъ슜?먭? ?ㅼ떆 ?숈씪??荑좏룿??諛쒓툒諛쏆쑝?ㅺ퀬 ?섎㈃ CONFLICT ?덉쇅媛 諛쒖깮?쒕떎.")
    void issueCoupon_AlreadyIssued_ShouldThrowException() {
        // given
        Long userId = 1L;
        CouponTemplate template = couponRepository.saveTemplate(
            new CouponTemplate("10% ?좎씤 荑좏룿", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        );

        // 泥?踰덉㎏ 諛쒓툒? ?깃났?댁빞 ??
        couponFacade.issueCoupon(userId, template.getId());

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
            couponFacade.issueCoupon(userId, template.getId())
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("?뺤긽?곸씤 諛쒓툒 ?붿껌 ??荑좏룿 諛쒓툒???깃났?섍퀬 ??λ맂??")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        CouponTemplate template = couponRepository.saveTemplate(
            new CouponTemplate("5000???좎씤 荑좏룿", CouponType.FIXED, new BigDecimal("5000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        );

        // when
        CouponIssue issue = couponFacade.issueCoupon(userId, template.getId());

        // then
        assertThat(issue).isNotNull();
        assertThat(issue.getUserId()).isEqualTo(userId);
        assertThat(issue.getCouponTemplateId()).isEqualTo(template.getId());

        // DB???ㅼ젣濡???λ릺?덈뒗吏 寃利?
        CouponIssue saved = couponRepository.findIssueById(issue.getId()).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCouponTemplateId()).isEqualTo(template.getId());
    }
}
