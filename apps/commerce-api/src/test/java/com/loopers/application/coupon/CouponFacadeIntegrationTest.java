package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponFacadeIntegrationTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplateModel saveTemplate(ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt));
    }

    @DisplayName("특정 쿠폰의 발급 내역을 조회할 때,")
    @Nested
    class GetIssuedCouponsByTemplateId {

        @DisplayName("발급된 쿠폰이 있으면 페이지네이션된 발급 내역을 반환한다.")
        @Test
        void returnsPagedIssuedCoupons_whenIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 1L));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 2L));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(2),
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getContent()).allMatch(info -> info.status() == CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("발급된 쿠폰이 없으면 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("템플릿이 만료된 경우 발급 쿠폰의 상태가 EXPIRED로 반환된다.")
        @Test
        void returnsExpiredStatus_whenTemplateIsExpired() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().minusDays(1));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 1L));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertThat(result.getContent().get(0).status()).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿의 발급 내역을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.getIssuedCouponsByTemplateId(99999L, PageRequest.of(0, 20)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
