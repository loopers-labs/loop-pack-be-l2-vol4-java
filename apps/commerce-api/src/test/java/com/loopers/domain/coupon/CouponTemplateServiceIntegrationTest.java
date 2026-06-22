package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponTemplateService;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponTemplateServiceIntegrationTest {

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("등록된 템플릿이 페이지네이션되어 반환된다.")
        @Test
        void returnsPaginatedTemplates_whenTemplatesExist() {
            // arrange
            LocalDateTime future = LocalDateTime.now().plusDays(7);
            couponTemplateJpaRepository.save(new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, future));
            couponTemplateJpaRepository.save(new CouponTemplateModel("20% 할인", CouponType.RATE, 20L, null, future));
            couponTemplateJpaRepository.save(new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, future));

            // act
            Page<CouponTemplateModel> result = couponTemplateService.getAll(PageRequest.of(0, 2));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }
}
