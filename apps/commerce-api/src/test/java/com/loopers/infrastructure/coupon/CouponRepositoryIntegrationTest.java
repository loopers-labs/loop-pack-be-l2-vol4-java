package com.loopers.infrastructure.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class CouponRepositoryIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel createCoupon(Integer minOrderAmount) {
        return CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build();
    }

    @DisplayName("쿠폰 템플릿을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여된다.")
        @Test
        void assignsId() {
            // arrange & act
            CouponModel savedCoupon = couponRepository.save(createCoupon(10_000));

            // assert
            assertThat(savedCoupon.getId()).isNotNull();
        }
    }

    @DisplayName("저장한 쿠폰 템플릿을 재조회할 때,")
    @Nested
    class FindById {

        @DisplayName("이름·할인 타입·할인 값·최소 주문 금액이 그대로 보존된다.")
        @Test
        void preservesFields_whenSavedWithMinOrderAmount() {
            // arrange
            CouponModel savedCoupon = couponRepository.save(createCoupon(10_000));

            // act
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(reloadedCoupon.getName().value()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(reloadedCoupon.getType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(reloadedCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(reloadedCoupon.getMinOrderAmount().value()).isEqualTo(10_000),
                () -> assertThat(reloadedCoupon.getExpiredAt().value()).isNotNull()
            );
        }

        @DisplayName("최소 주문 금액 없이 저장하면 재조회 시 제약 없음(0)으로 보존된다.")
        @Test
        void preservesNoneMinOrderAmount_whenSavedWithout() {
            // arrange
            CouponModel savedCoupon = couponRepository.save(createCoupon(null));

            // act
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertThat(reloadedCoupon.getMinOrderAmount().value()).isZero();
        }
    }

    @DisplayName("활성 쿠폰 템플릿을 식별자로 조회할 때,")
    @Nested
    class GetActiveById {

        @DisplayName("활성 템플릿은 반환하고, 삭제됐거나 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void returnsActiveCoupon_andThrowsOtherwise() {
            // arrange
            CouponModel activeCoupon = couponRepository.save(createCoupon(10_000));
            CouponModel deletedCoupon = couponRepository.save(createCoupon(10_000));
            deletedCoupon.delete();
            couponJpaRepository.saveAndFlush(deletedCoupon);

            // act & assert
            assertAll(
                () -> assertThat(couponRepository.getActiveById(activeCoupon.getId()).getId()).isEqualTo(activeCoupon.getId()),
                () -> assertThatThrownBy(() -> couponRepository.getActiveById(deletedCoupon.getId()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThatThrownBy(() -> couponRepository.getActiveById(-1L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND)
            );
        }
    }

    @DisplayName("활성 쿠폰 템플릿을 식별자로 탐색할 때,")
    @Nested
    class FindActiveById {

        @DisplayName("삭제되지 않은 템플릿이면 해당 템플릿을 담은 Optional을 반환한다.")
        @Test
        void returnsPresent_whenCouponIsActive() {
            // arrange
            CouponModel savedCoupon = couponRepository.save(createCoupon(10_000));

            // act & assert
            assertThat(couponRepository.findActiveById(savedCoupon.getId())).isPresent();
        }

        @DisplayName("이미 삭제됐거나 없으면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenCouponIsDeletedOrAbsent() {
            // arrange
            CouponModel deletedCoupon = couponRepository.save(createCoupon(10_000));
            deletedCoupon.delete();
            couponJpaRepository.saveAndFlush(deletedCoupon);

            // act & assert
            assertAll(
                () -> assertThat(couponRepository.findActiveById(deletedCoupon.getId())).isEmpty(),
                () -> assertThat(couponRepository.findActiveById(-1L)).isEmpty()
            );
        }
    }

    @DisplayName("활성 쿠폰 템플릿을 페이지로 조회할 때,")
    @Nested
    class FindActiveByPage {

        private CouponModel namedCoupon(String name) {
            return CouponModel.builder()
                .rawName(name)
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(ZonedDateTime.now().plusDays(7))
                .now(ZonedDateTime.now())
                .build();
        }

        @DisplayName("삭제된 템플릿을 제외하고 등록 시각 내림차순으로 페이징한다.")
        @Test
        void returnsActivePage_excludingDeleted_sortedByCreatedAtDesc() {
            // arrange
            couponRepository.save(namedCoupon("쿠폰1"));
            couponRepository.save(namedCoupon("쿠폰2"));
            CouponModel deletedCoupon = couponRepository.save(namedCoupon("쿠폰3"));
            deletedCoupon.delete();
            couponJpaRepository.saveAndFlush(deletedCoupon);

            // act
            Page<CouponModel> couponPage = couponRepository.findActiveByPage(0, 10);

            // assert
            assertAll(
                () -> assertThat(couponPage.getTotalElements()).isEqualTo(2),
                () -> assertThat(couponPage.getContent())
                    .extracting(coupon -> coupon.getName().value())
                    .containsExactlyInAnyOrder("쿠폰1", "쿠폰2"),
                () -> assertThat(couponPage.getContent())
                    .extracting(CouponModel::getCreatedAt)
                    .isSortedAccordingTo(Comparator.reverseOrder())
            );
        }

        @DisplayName("2페이지를 요청하면, 오프셋이 적용되어 해당 페이지 항목만 반환된다.")
        @Test
        void returnsSecondPage_withOffset() {
            // arrange
            couponRepository.save(namedCoupon("쿠폰1"));
            couponRepository.save(namedCoupon("쿠폰2"));
            couponRepository.save(namedCoupon("쿠폰3"));

            // act
            Page<CouponModel> firstPage = couponRepository.findActiveByPage(0, 2);
            Page<CouponModel> secondPage = couponRepository.findActiveByPage(1, 2);

            // assert
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getContent()).hasSize(2),
                () -> assertThat(secondPage.getContent()).hasSize(1)
            );
        }

        @DisplayName("만료 시각이 지난 활성 템플릿도 목록에 포함된다.")
        @Test
        void includesExpiredCoupon() {
            // arrange (과거 기준 시각으로 생성해, 실제로는 만료 시각이 지난 활성 템플릿을 만든다)
            ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);
            couponRepository.save(CouponModel.builder()
                .rawName("만료 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(pastExpiredAt)
                .now(pastExpiredAt.minusDays(1))
                .build());

            // act
            Page<CouponModel> couponPage = couponRepository.findActiveByPage(0, 10);

            // assert
            assertThat(couponPage.getContent())
                .extracting(coupon -> coupon.getName().value())
                .contains("만료 쿠폰");
        }
    }
}
