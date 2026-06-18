package com.loopers.domain.coupon;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponIssueIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("?숈씪???ъ슜?먯뿉寃??숈씪??荑좏룿??以묐났 諛쒓툒?섎㈃ ?좊땲???쒖빟 議곌굔 ?꾨컲 ?덉쇅媛 諛쒖깮?댁빞 ?쒕떎.")
    void saveIssue_DuplicateUserAndTemplate_ShouldThrowException() {
        // given
        Long userId = 1L;
        CouponTemplate template = new CouponTemplate("test", CouponType.FIXED, new java.math.BigDecimal("1000"), java.math.BigDecimal.ZERO, null, java.time.LocalDateTime.now().plusDays(1));
        template = couponRepository.saveTemplate(template);

        CouponIssue firstIssue = new CouponIssue(userId, template);
        couponRepository.saveIssue(firstIssue);

        // when & then
        CouponIssue secondIssue = new CouponIssue(userId, template);
        assertThrows(DataIntegrityViolationException.class, () -> {
            couponRepository.saveIssue(secondIssue);
        });
    }
}
