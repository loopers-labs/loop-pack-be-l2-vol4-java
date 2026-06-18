package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueTest {

    @Test
    @DisplayName("理쒖냼 二쇰Ц 湲덉븸蹂대떎 二쇰Ц 湲덉븸???곸쑝硫?荑좏룿 ?ъ슜 ???덉쇅媛 諛쒖깮?쒕떎.")
    void use_UnderMinOrderAmount_ShouldThrowException() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("荑좏룿", CouponType.FIXED, new BigDecimal("1000"), new BigDecimal("10000"), null, now.plusDays(1));
        // template??id ?명똿???쒕??덉씠?섑븯湲??꾪빐 reflection ?ъ슜 (JPA ?섏〈???놁씠)
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        CouponIssue issue = new CouponIssue(1L, template);

        // when & then
        assertThrows(CoreException.class, () -> issue.use(new BigDecimal("9999"), now));
    }

    @Test
    @DisplayName("?대? ?ъ슜??USED) 荑좏룿???ъ슜?섎젮怨??섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void use_AlreadyUsed_ShouldThrowException() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("荑좏룿", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        CouponIssue issue = new CouponIssue(1L, template);

        // 泥?踰덉㎏ ?ъ슜?쇰줈 USED ?곹깭媛 ??
        issue.use(new BigDecimal("5000"), now);

        // ??踰덉㎏ ?ъ슜 ?쒕룄 ???덉쇅 諛쒖깮
        assertThrows(CoreException.class, () -> issue.use(new BigDecimal("5000"), now));
    }

    @Test
    @DisplayName("?뺤븸(FIXED) 荑좏룿? 怨좎젙??湲덉븸留뚰겮 ?좎씤?섎ŉ 二쇰Ц 湲덉븸??珥덇낵???좎씤?????녿떎.")
    void use_FixedCoupon_ShouldCalculateDiscount() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("泥쒖썝?좎씤", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        
        // 1踰?耳?댁뒪: 5000??二쇰Ц??1000???좎씤
        CouponIssue issue1 = new CouponIssue(1L, template);
        BigDecimal discount = issue1.use(new BigDecimal("5000"), now);
        assertThat(discount).isEqualByComparingTo("1000");

        // 2踰?耳?댁뒪: 500??二쇰Ц??500???좎씤 (二쇰Ц 湲덉븸 珥덇낵 遺덇?)
        CouponIssue issue2 = new CouponIssue(1L, template);
        BigDecimal discountExceed = issue2.use(new BigDecimal("500"), now);
        assertThat(discountExceed).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("?뺣쪧(RATE) 荑좏룿? 鍮꾩쑉留뚰겮 ?좎씤?섎ŉ 理쒕? ?쒕룄(maxDiscountAmount)媛 ?덉쑝硫??곸슜?쒕떎.")
    void use_RateCoupon_ShouldCalculateDiscountWithLimit() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("10%?좎씤", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("2000"), now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        
        CouponIssue issue1 = new CouponIssue(1L, template);
        CouponIssue issue2 = new CouponIssue(1L, template);

        // when
        BigDecimal discount1 = issue1.use(new BigDecimal("10000"), now);
        BigDecimal discount2 = issue2.use(new BigDecimal("30000"), now);

        // then
        assertThat(discount1).isEqualByComparingTo("1000");
        assertThat(discount2).isEqualByComparingTo("2000");
    }
}
