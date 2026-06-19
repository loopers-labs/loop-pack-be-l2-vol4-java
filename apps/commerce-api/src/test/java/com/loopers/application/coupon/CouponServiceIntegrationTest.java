package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
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

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponServiceIntegrationTest {

    @Autowired private CouponService couponService;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel saveCoupon(String name, CouponType type, int value) {
        return couponJpaRepository.save(new CouponModel(name, type, value, null, FUTURE));
    }

    @DisplayName("create()를 호출할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 커맨드로 생성 시 DB에 저장되고 ID가 부여된 CouponInfo가 반환된다.")
        @Test
        void savesCoupon_whenValidCommandProvided() {
            // arrange
            CouponCreateCommand command = new CouponCreateCommand("신규가입 10% 할인", CouponType.RATE, 10, 5_000, FUTURE);

            // act
            CouponInfo result = couponService.create(command);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(result.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(result.value()).isEqualTo(10),
                () -> assertThat(couponJpaRepository.findById(result.id())).isPresent()
            );
        }
    }

    @DisplayName("getById()를 호출할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 쿠폰 조회 시 CouponInfo가 반환된다.")
        @Test
        void returnsCouponInfo_whenCouponExists() {
            // arrange
            CouponModel saved = saveCoupon("정액쿠폰", CouponType.FIXED, 3_000);

            // act
            CouponInfo result = couponService.getById(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("정액쿠폰")
            );
        }

        @DisplayName("존재하지 않는 ID 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class, () -> couponService.getById(999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("소프트딜리트된 쿠폰 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsDeleted() {
            // arrange
            CouponModel saved = saveCoupon("삭제쿠폰", CouponType.FIXED, 1_000);
            couponService.delete(saved.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> couponService.getById(saved.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAll()을 호출할 때,")
    @Nested
    class GetAll {

        @DisplayName("삭제된 쿠폰은 제외하고 활성 쿠폰만 반환된다.")
        @Test
        void returnsOnlyActiveCoupons() {
            // arrange
            saveCoupon("쿠폰A", CouponType.FIXED, 1_000);
            saveCoupon("쿠폰B", CouponType.RATE, 5);
            CouponModel deleted = saveCoupon("삭제쿠폰", CouponType.FIXED, 500);
            couponService.delete(deleted.getId());

            // act
            Page<CouponInfo> result = couponService.getAll(PageRequest.of(0, 20));

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent()).extracting(CouponInfo::name)
                    .containsExactlyInAnyOrder("쿠폰A", "쿠폰B")
            );
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시 변경된 내용이 DB에 반영된다.")
        @Test
        void updatesPersisted_whenValidCommandProvided() {
            // arrange
            CouponModel saved = saveCoupon("기존쿠폰", CouponType.FIXED, 1_000);
            CouponUpdateCommand command = new CouponUpdateCommand("수정쿠폰", CouponType.RATE, 15, 5_000, FUTURE);

            // act
            CouponInfo result = couponService.update(saved.getId(), command);

            // assert
            CouponModel updated = couponJpaRepository.findById(saved.getId()).orElseThrow();
            assertAll(
                () -> assertThat(result.name()).isEqualTo("수정쿠폰"),
                () -> assertThat(updated.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(updated.getValue()).isEqualTo(15),
                () -> assertThat(updated.getMinOrderAmount()).isEqualTo(5_000)
            );
        }
    }

    @DisplayName("delete()를 호출할 때,")
    @Nested
    class Delete {

        @DisplayName("쿠폰 삭제 시 DB에 소프트딜리트되어 deleted_at이 설정된다.")
        @Test
        void softDeletesCoupon_whenCalled() {
            // arrange
            CouponModel saved = saveCoupon("삭제대상쿠폰", CouponType.FIXED, 1_000);

            // act
            couponService.delete(saved.getId());

            // assert
            CouponModel found = couponJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }
    }

    @DisplayName("getIssues()를 호출할 때,")
    @Nested
    class GetIssues {

        @DisplayName("해당 쿠폰의 발급 내역이 반환된다.")
        @Test
        void returnsIssuedCoupons_whenIssuesExist() {
            // arrange
            CouponModel coupon = saveCoupon("발급쿠폰", CouponType.RATE, 10);
            userCouponJpaRepository.save(new UserCouponModel(1L, coupon));
            userCouponJpaRepository.save(new UserCouponModel(2L, coupon));

            // act
            Page<UserCouponInfo> result = couponService.getIssues(coupon.getId(), PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("존재하지 않는 쿠폰의 발급 내역 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.getIssues(999L, PageRequest.of(0, 20)));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
