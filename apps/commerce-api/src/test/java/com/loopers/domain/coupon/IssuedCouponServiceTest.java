package com.loopers.domain.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuedCouponServiceTest {

    private IssuedCouponService issuedCouponService;
    private IssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        issuedCouponRepository = mock(IssuedCouponRepository.class);
        issuedCouponService = new IssuedCouponService(issuedCouponRepository);
    }

    @DisplayName("특정 쿠폰 템플릿의 발급 내역을 페이지 단위로 조회할 때,")
    @Nested
    class FindAllByCouponTemplateId {

        @DisplayName("발급 내역이 존재하면 해당 목록이 페이지 단위로 반환된다.")
        @Test
        void issuedCouponsAreListedByPage_whenIssuesExist() {
            // given
            Long couponTemplateId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            IssuedCouponModel issued = new IssuedCouponModel(couponTemplateId, 10L);
            when(issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable))
                    .thenReturn(new PageImpl<>(List.of(issued)));

            // when
            Page<IssuedCouponModel> result = issuedCouponService.findAllByCouponTemplateId(couponTemplateId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @DisplayName("발급 내역이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoIssuesExist() {
            // given
            Long couponTemplateId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            when(issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            // when
            Page<IssuedCouponModel> result = issuedCouponService.findAllByCouponTemplateId(couponTemplateId, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @DisplayName("내 발급 쿠폰 목록을 조회할 때,")
    @Nested
    class GetMyIssuedCoupons {

        @DisplayName("발급받은 쿠폰이 있으면 해당 목록이 반환된다.")
        @Test
        void myIssuedCouponsAreListed_whenCouponsExist() {
            // given
            Long userId = 10L;
            IssuedCouponModel issued1 = new IssuedCouponModel(1L, userId);
            IssuedCouponModel issued2 = new IssuedCouponModel(2L, userId);
            when(issuedCouponRepository.findAllByUserId(userId))
                    .thenReturn(List.of(issued1, issued2));

            // when
            List<IssuedCouponModel> result = issuedCouponService.getMyIssuedCoupons(userId);

            // then
            assertThat(result).hasSize(2);
        }

        @DisplayName("발급받은 쿠폰이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoCouponsExist() {
            // given
            Long userId = 10L;
            when(issuedCouponRepository.findAllByUserId(userId))
                    .thenReturn(List.of());

            // when
            List<IssuedCouponModel> result = issuedCouponService.getMyIssuedCoupons(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 템플릿 ID와 유저 ID로 발급하면 AVAILABLE 상태의 발급 쿠폰이 반환된다.")
        @Test
        void issuedCouponIsReturned_withAvailableStatus() {
            // given
            Long couponTemplateId = 1L;
            Long userId = 10L;
            when(issuedCouponRepository.save(any(IssuedCouponModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            IssuedCouponModel result = issuedCouponService.issue(couponTemplateId, userId);

            // then
            assertAll(
                    () -> assertThat(result.getCouponTemplateId()).isEqualTo(couponTemplateId),
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }
}
