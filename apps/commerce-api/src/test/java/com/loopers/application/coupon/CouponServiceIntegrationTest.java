package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponEntity saveCoupon(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount,
                                    ZonedDateTime expiredAt) {
        return couponJpaRepository.save(new CouponEntity(name, type, value, minOrderAmount, expiredAt));
    }

    private IssuedCouponEntity saveIssuedCoupon(Long couponId, Long userId, ZonedDateTime expiredAt) {
        return issuedCouponJpaRepository.save(new IssuedCouponEntity(couponId, userId, expiredAt));
    }

    private IssuedCouponEntity saveUsedIssuedCoupon(Long couponId, Long userId, ZonedDateTime expiredAt) {
        IssuedCouponEntity entity = issuedCouponJpaRepository.save(new IssuedCouponEntity(couponId, userId, expiredAt));
        IssuedCoupon used = new IssuedCoupon(entity.getId(), couponId, userId, CouponStatus.USED, ZonedDateTime.now(),
            expiredAt, entity.getCreatedAt(), entity.getUpdatedAt(), null);
        entity.updateFrom(used);
        return issuedCouponJpaRepository.save(entity);
    }

    @DisplayName("쿠폰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰이고 발급 이력이 없으면, AVAILABLE 상태의 IssuedCoupon이 저장된다.")
        @Test
        void savesIssuedCoupon_whenValidCouponAndNoHistory() {
            CouponEntity coupon = saveCoupon("10% 할인 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));

            IssuedCoupon result = couponService.issue(coupon.getId(), 1L);

            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getCouponId()).isEqualTo(coupon.getId()),
                () -> assertThat(result.getUserId()).isEqualTo(1L),
                () -> assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(9999L, 1L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsDeleted() {
            CouponEntity coupon = saveCoupon("삭제된 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(30));
            couponService.deleteCoupon(coupon.getId());

            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(coupon.getId(), 1L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("동일 유저에게 이미 발급된 쿠폰이라도, 중복 발급된다.")
        @Test
        void savesIssuedCoupon_evenWhenAlreadyIssuedToSameUser() {
            CouponEntity coupon = saveCoupon("중복 발급 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            IssuedCoupon result = couponService.issue(coupon.getId(), 1L);

            assertThat(result.getUserId()).isEqualTo(1L);
        }
    }

    @DisplayName("유저 쿠폰 목록 조회 시,")
    @Nested
    class GetUserCoupons {

        @DisplayName("발급된 쿠폰이 없으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenNoIssuedCoupons() {
            List<CouponInfo.MyCoupon> result = couponService.getUserCoupons(1L);
            assertThat(result).isEmpty();
        }

        @DisplayName("만료일이 남은 AVAILABLE 쿠폰이면, AVAILABLE 상태로 반환한다.")
        @Test
        void returnsAvailable_whenCouponIsAvailableAndNotExpired() {
            CouponEntity coupon = saveCoupon("유효 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            List<CouponInfo.MyCoupon> result = couponService.getUserCoupons(1L);

            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("USED 상태 쿠폰이면, USED 상태로 반환한다.")
        @Test
        void returnsUsed_whenCouponIsUsed() {
            CouponEntity coupon = saveCoupon("사용 완료 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            saveUsedIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            List<CouponInfo.MyCoupon> result = couponService.getUserCoupons(1L);

            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).status()).isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("만료일이 지난 AVAILABLE 상태 쿠폰이면, EXPIRED 상태로 반환한다.")
        @Test
        void returnsExpired_whenCouponExpiredButStatusIsStillAvailable() {
            CouponEntity coupon = saveCoupon("만료 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().minusDays(1));
            saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            List<CouponInfo.MyCoupon> result = couponService.getUserCoupons(1L);

            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).status()).isEqualTo(CouponStatus.EXPIRED)
            );
        }

        @DisplayName("여러 상태의 쿠폰이 섞여 있으면, 각각 올바른 상태로 반환된다.")
        @Test
        void returnsMixedStatuses_whenCouponsHaveDifferentStatuses() {
            CouponEntity available = saveCoupon("유효", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            CouponEntity used = saveCoupon("사용완료", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(30));
            CouponEntity expired = saveCoupon("만료", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().minusDays(1));

            saveIssuedCoupon(available.getId(), 1L, available.getExpiredAt());
            saveUsedIssuedCoupon(used.getId(), 1L, used.getExpiredAt());
            saveIssuedCoupon(expired.getId(), 1L, expired.getExpiredAt());

            List<CouponInfo.MyCoupon> result = couponService.getUserCoupons(1L);

            assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result).anyMatch(c -> c.status() == CouponStatus.AVAILABLE),
                () -> assertThat(result).anyMatch(c -> c.status() == CouponStatus.USED),
                () -> assertThat(result).anyMatch(c -> c.status() == CouponStatus.EXPIRED)
            );
        }
    }

    @DisplayName("쿠폰 사용 처리 시,")
    @Nested
    class Use {

        @DisplayName("유효한 FIXED 쿠폰이면, 정액 할인 금액을 반환하고 상태가 USED로 변경된다.")
        @Test
        void returnsFixedDiscount_andChangesStatusToUsed() {
            CouponEntity coupon = saveCoupon("정액 쿠폰", CouponType.FIXED, BigDecimal.valueOf(3000), null,
                ZonedDateTime.now().plusDays(30));
            IssuedCouponEntity issued = saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            BigDecimal discount = couponService.use(issued.getId(), 1L, BigDecimal.valueOf(10000));

            IssuedCouponEntity saved = issuedCouponJpaRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                () -> assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000)),
                () -> assertThat(saved.getStatus()).isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("유효한 RATE 쿠폰이면, 정률 할인 금액을 반환하고 상태가 USED로 변경된다.")
        @Test
        void returnsRateDiscount_andChangesStatusToUsed() {
            // 20000 * 10% = 2000
            CouponEntity coupon = saveCoupon("10% 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            IssuedCouponEntity issued = saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            BigDecimal discount = couponService.use(issued.getId(), 1L, BigDecimal.valueOf(20000));

            IssuedCouponEntity saved = issuedCouponJpaRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                () -> assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(2000)),
                () -> assertThat(saved.getStatus()).isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("존재하지 않는 발급 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIssuedCouponDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(9999L, 1L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("발급 쿠폰 소유자가 아니면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenRequesterIsNotOwner() {
            CouponEntity coupon = saveCoupon("쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            IssuedCouponEntity issued = saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(issued.getId(), 2L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 사용된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponAlreadyUsed() {
            CouponEntity coupon = saveCoupon("사용된 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            IssuedCouponEntity issued = saveUsedIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(issued.getId(), 1L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponIsExpired() {
            CouponEntity coupon = saveCoupon("만료 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().minusDays(1));
            IssuedCouponEntity issued = saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(issued.getId(), 1L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("최소 주문 금액 조건을 충족하지 못하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountBelowMinOrderAmount() {
            CouponEntity coupon = saveCoupon("최소금액 쿠폰", CouponType.RATE, BigDecimal.TEN,
                BigDecimal.valueOf(20000), ZonedDateTime.now().plusDays(30));
            IssuedCouponEntity issued = saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());

            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(issued.getId(), 1L, BigDecimal.valueOf(5000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 생성 시,")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 FIXED 쿠폰 정보이면, 쿠폰이 저장된다.")
        @Test
        void createsCoupon_whenValidFixedCoupon() {
            CouponCommand.Create command = new CouponCommand.Create(
                "정액 쿠폰", CouponType.FIXED, BigDecimal.valueOf(5000), null,
                ZonedDateTime.now().plusDays(30));

            Coupon result = couponService.createCoupon(command);

            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getName()).isEqualTo("정액 쿠폰"),
                () -> assertThat(result.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.valueOf(5000))
            );
        }

        @DisplayName("유효한 RATE 쿠폰 정보이면, 최소 주문 금액 포함하여 저장된다.")
        @Test
        void createsCoupon_whenValidRateCouponWithMinOrderAmount() {
            CouponCommand.Create command = new CouponCommand.Create(
                "정률 쿠폰", CouponType.RATE, BigDecimal.TEN, BigDecimal.valueOf(10000),
                ZonedDateTime.now().plusDays(30));

            Coupon result = couponService.createCoupon(command);

            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.TEN),
                () -> assertThat(result.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000))
            );
        }
    }

    @DisplayName("쿠폰 수정 시,")
    @Nested
    class UpdateCoupon {

        @DisplayName("존재하는 쿠폰이면, 쿠폰 정보가 수정된다.")
        @Test
        void updatesCoupon_whenCouponExists() {
            CouponEntity coupon = saveCoupon("기존 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            CouponCommand.Update command = new CouponCommand.Update(
                "수정된 쿠폰", CouponType.FIXED, BigDecimal.valueOf(2000), null,
                ZonedDateTime.now().plusDays(60));

            Coupon result = couponService.updateCoupon(coupon.getId(), command);

            assertAll(
                () -> assertThat(result.getName()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(result.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.valueOf(2000))
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CouponCommand.Update command = new CouponCommand.Update(
                "수정", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(30));

            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.updateCoupon(9999L, command));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 삭제 시,")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰이면, deletedAt이 설정되어 Soft Delete된다.")
        @Test
        void softDeletesCoupon_whenCouponExists() {
            CouponEntity coupon = saveCoupon("삭제할 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));

            couponService.deleteCoupon(coupon.getId());

            CouponEntity deleted = couponJpaRepository.findById(coupon.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () -> couponService.deleteCoupon(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 삭제된 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponAlreadyDeleted() {
            CouponEntity coupon = saveCoupon("이미 삭제된 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            couponService.deleteCoupon(coupon.getId());

            CoreException ex = assertThrows(CoreException.class, () -> couponService.deleteCoupon(coupon.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 단건 조회 시,")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰이면, 쿠폰을 반환한다.")
        @Test
        void returnsCoupon_whenCouponExists() {
            CouponEntity coupon = saveCoupon("조회 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));

            Coupon result = couponService.getCoupon(coupon.getId());

            assertThat(result.getId()).isEqualTo(coupon.getId());
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () -> couponService.getCoupon(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsDeleted() {
            CouponEntity coupon = saveCoupon("삭제된 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            couponService.deleteCoupon(coupon.getId());

            CoreException ex = assertThrows(CoreException.class, () -> couponService.getCoupon(coupon.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 목록 조회 시,")
    @Nested
    class GetCoupons {

        @DisplayName("삭제되지 않은 쿠폰만 페이징하여 반환한다.")
        @Test
        void returnsCouponsExcludingDeleted() {
            saveCoupon("쿠폰1", CouponType.RATE, BigDecimal.TEN, null, ZonedDateTime.now().plusDays(30));
            saveCoupon("쿠폰2", CouponType.FIXED, BigDecimal.valueOf(1000), null, ZonedDateTime.now().plusDays(30));
            CouponEntity toDelete = saveCoupon("삭제 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            couponService.deleteCoupon(toDelete.getId());

            Page<Coupon> result = couponService.getCoupons(PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("쿠폰이 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoCoupons() {
            Page<Coupon> result = couponService.getCoupons(PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @DisplayName("발급 쿠폰 내역 조회 시,")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("존재하는 쿠폰이면, 해당 쿠폰의 발급 내역을 페이징으로 반환한다.")
        @Test
        void returnsIssuedCoupons_whenCouponExists() {
            CouponEntity coupon = saveCoupon("발급 내역 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));
            saveIssuedCoupon(coupon.getId(), 1L, coupon.getExpiredAt());
            saveIssuedCoupon(coupon.getId(), 2L, coupon.getExpiredAt());

            Page<IssuedCoupon> result = couponService.getIssuedCoupons(coupon.getId(), PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("발급 이력이 없는 쿠폰이면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuedCoupons() {
            CouponEntity coupon = saveCoupon("미발급 쿠폰", CouponType.RATE, BigDecimal.TEN, null,
                ZonedDateTime.now().plusDays(30));

            Page<IssuedCoupon> result = couponService.getIssuedCoupons(coupon.getId(), PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isZero();
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.getIssuedCoupons(9999L, PageRequest.of(0, 10)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
