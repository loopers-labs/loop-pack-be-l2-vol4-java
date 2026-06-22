package com.loopers.domain.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.coupon.UserCouponService;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserCouponServiceIntegrationTest {

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("내 쿠폰 목록을 조회할 때,")
    @Nested
    class GetMyCoupons {

        @DisplayName("memberId로 조회하면 템플릿 정보(이름, 만료일, 상태)가 함께 반환된다.")
        @Test
        void returnsCouponsWithTemplateInfo_whenMemberIdGiven() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));
            userCouponJpaRepository.save(new UserCouponModel(1L, template.getId()));

            // act
            List<UserCouponInfo> result = userCouponService.getMyCoupons(1L);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).templateName()).isEqualTo("10% 할인");
            assertThat(result.get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
        }
    }

    @DisplayName("발급 내역을 조회할 때,")
    @Nested
    class GetIssuances {

        @DisplayName("templateId로 조회하면 페이지네이션된 발급 목록이 반환된다.")
        @Test
        void returnsPaginatedIssuances_whenTemplateIdGiven() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));
            userCouponJpaRepository.save(new UserCouponModel(1L, template.getId()));
            userCouponJpaRepository.save(new UserCouponModel(2L, template.getId()));
            userCouponJpaRepository.save(new UserCouponModel(3L, template.getId()));

            // act
            Page<UserCouponModel> result = userCouponService.getIssuances(template.getId(), PageRequest.of(0, 2));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).hasSize(2);
        }
    }

    @DisplayName("동일 회원이 같은 쿠폰 템플릿을 중복 발급할 때,")
    @Nested
    class DuplicateIssuance {

        @DisplayName("UNIQUE 제약 위반으로 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateIssuance() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));
            userCouponJpaRepository.saveAndFlush(new UserCouponModel(1L, template.getId()));

            // act & assert
            assertThatThrownBy(() -> userCouponJpaRepository.saveAndFlush(new UserCouponModel(1L, template.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
