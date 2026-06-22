package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponRepository;
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
    @DisplayName("동일한 사용자에게 동일한 쿠폰을 중복 발급하면 유니크 제약 조건 위반 예외가 발생해야 한다.")
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
