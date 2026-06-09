package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponAdminServiceTest {

    private static final ZonedDateTime EXPIRES = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponAdminService couponAdminService = new CouponAdminService(couponRepository);

    private Coupon coupon() {
        return Coupon.create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES);
    }

    @Test
    @DisplayName("create 커맨드로 쿠폰 템플릿을 저장한다")
    void givenCreateCommand_whenCreate_thenSavesCoupon() {
        CouponCommand.Create command =
                new CouponCommand.Create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        couponAdminService.create(command);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        Coupon saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getName()).isEqualTo("신규가입 3천원"),
                () -> assertThat(saved.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(saved.getValue()).isEqualTo(3_000L),
                () -> assertThat(saved.getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(saved.getExpiredAt()).isEqualTo(EXPIRES)
        );
    }

    @Test
    @DisplayName("create 시 정률 값이 100 을 초과하면 검증에 실패하고 저장하지 않는다")
    void givenInvalidRate_whenCreate_thenThrowsAndDoesNotSave() {
        CouponCommand.Create command =
                new CouponCommand.Create("잘못된 정률", CouponType.RATE, 150L, null, EXPIRES);

        assertThatThrownBy(() -> couponAdminService.create(command))
                .isInstanceOf(CoreException.class);
        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("update 로 기존 쿠폰의 정책을 수정한다")
    void givenUpdateCommand_whenUpdate_thenChangesFields() {
        Coupon existing = coupon();
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));

        CouponCommand.Update command =
                new CouponCommand.Update(1L, "정률 15%", CouponType.RATE, 15L, 20_000L, EXPIRES);
        CouponResult.Detail result = couponAdminService.update(command);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("정률 15%"),
                () -> assertThat(result.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(result.value()).isEqualTo(15L),
                () -> assertThat(result.minOrderAmount()).isEqualTo(20_000L)
        );
    }

    @Test
    @DisplayName("update 시 쿠폰이 없으면 COUPON_NOT_FOUND 가 발생한다")
    void givenMissingCoupon_whenUpdate_thenThrowsNotFound() {
        when(couponRepository.findById(99L)).thenReturn(Optional.empty());

        CouponCommand.Update command =
                new CouponCommand.Update(99L, "이름", CouponType.FIXED, 1_000L, null, EXPIRES);
        assertThatThrownBy(() -> couponAdminService.update(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("delete 로 쿠폰을 소프트 삭제한다")
    void givenCoupon_whenDelete_thenSoftDeletes() {
        Coupon existing = coupon();
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));

        couponAdminService.delete(1L);

        assertThat(existing.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete 시 쿠폰이 없으면 COUPON_NOT_FOUND 가 발생한다")
    void givenMissingCoupon_whenDelete_thenThrowsNotFound() {
        when(couponRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponAdminService.delete(99L))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("getCoupon 으로 단건 상세를 조회한다")
    void givenCoupon_whenGetCoupon_thenReturnsDetail() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon()));

        CouponResult.Detail result = couponAdminService.getCoupon(1L);

        assertThat(result.name()).isEqualTo("신규가입 3천원");
    }

    @Test
    @DisplayName("getCoupons 로 페이지를 조회해 상세로 매핑한다")
    void givenCoupons_whenGetCoupons_thenReturnsPageOfDetail() {
        Pageable pageable = PageRequest.of(0, 20);
        when(couponRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(coupon()), pageable, 1));

        var result = couponAdminService.getCoupons(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("신규가입 3천원");
    }
}
