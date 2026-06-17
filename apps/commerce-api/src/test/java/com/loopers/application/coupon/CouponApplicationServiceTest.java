package com.loopers.application.coupon;

import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CouponApplicationServiceTest {

    private MemberService memberService;
    private CouponTemplateRepository couponTemplateRepository;
    private IssuedCouponRepository issuedCouponRepository;
    private CouponApplicationService couponApplicationService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        couponTemplateRepository = mock(CouponTemplateRepository.class);
        issuedCouponRepository = mock(IssuedCouponRepository.class);
        couponApplicationService = new CouponApplicationService(memberService, couponTemplateRepository, issuedCouponRepository);
    }

    @DisplayName("쿠폰 발급 요청 시, ")
    @Nested
    class IssueCoupon {

        @DisplayName("정상 요청이면 쿠폰이 발급된다.")
        @Test
        void issuesCoupon_whenValid() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            CouponTemplate template = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, null,
                ZonedDateTime.now().plusDays(30));
            when(couponTemplateRepository.findById(10L)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.existsByMemberIdAndCouponTemplateId(1L, 10L)).thenReturn(false);
            when(issuedCouponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            couponApplicationService.issueCoupon("user1", 10L);

            verify(issuedCouponRepository).save(any(IssuedCoupon.class));
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);
            when(couponTemplateRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () ->
                couponApplicationService.issueCoupon("user1", 999L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 템플릿이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateIsExpired() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            CouponTemplate template = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, null,
                ZonedDateTime.now().minusDays(1));
            when(couponTemplateRepository.findById(10L)).thenReturn(Optional.of(template));

            CoreException ex = assertThrows(CoreException.class, () ->
                couponApplicationService.issueCoupon("user1", 10L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급받은 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            CouponTemplate template = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, null,
                ZonedDateTime.now().plusDays(30));
            when(couponTemplateRepository.findById(10L)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.existsByMemberIdAndCouponTemplateId(1L, 10L)).thenReturn(true);

            CoreException ex = assertThrows(CoreException.class, () ->
                couponApplicationService.issueCoupon("user1", 10L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시, ")
    @Nested
    class GetMyCoupons {

        @DisplayName("보유한 쿠폰 목록과 각 쿠폰의 템플릿 정보를 함께 반환한다.")
        @Test
        void returnsMyCoupons() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            IssuedCoupon issued = IssuedCoupon.create(1L, 10L);
            when(issuedCouponRepository.findAllByMemberId(1L)).thenReturn(List.of(issued));

            CouponTemplate template = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, null,
                ZonedDateTime.now().plusDays(30));
            when(couponTemplateRepository.findById(10L)).thenReturn(Optional.of(template));

            List<MyCouponInfo> result = couponApplicationService.getMyCoupons("user1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(result.get(0).couponName()).isEqualTo("1000원 할인");
        }
    }
}
