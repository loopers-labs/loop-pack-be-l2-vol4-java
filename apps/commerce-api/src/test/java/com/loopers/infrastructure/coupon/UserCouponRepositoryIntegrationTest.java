package com.loopers.infrastructure.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserCouponRepositoryIntegrationTest {

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel coupon(Long couponId, Integer minOrderAmount) {
        CouponModel coupon = CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build();
        ReflectionTestUtils.setField(coupon, "id", couponId);

        return coupon;
    }

    @DisplayName("발급 쿠폰을 저장한 뒤 재조회할 때,")
    @Nested
    class SaveAndFind {

        @DisplayName("스냅샷 값이 그대로 보존되고 식별자가 부여된다.")
        @Test
        void preservesSnapshot_andAssignsId() {
            // arrange
            UserCouponModel issuedCoupon = UserCouponModel.issue(100L, coupon(1L, 10_000));

            // act
            UserCouponModel savedCoupon = userCouponRepository.save(issuedCoupon);
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(reloadedCoupon.getId()).isNotNull(),
                () -> assertThat(reloadedCoupon.getUserId()).isEqualTo(100L),
                () -> assertThat(reloadedCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(reloadedCoupon.getName()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(reloadedCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(reloadedCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(reloadedCoupon.getMinOrderAmount()).isEqualTo(10_000),
                () -> assertThat(reloadedCoupon.getExpiredAt()).isNotNull(),
                () -> assertThat(reloadedCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("최소 주문 금액이 0인 템플릿에서 발급하면 0이 그대로 보존된다.")
        @Test
        void preservesZeroMinOrderAmount() {
            // arrange
            UserCouponModel issuedCoupon = UserCouponModel.issue(100L, coupon(1L, null));

            // act
            UserCouponModel savedCoupon = userCouponRepository.save(issuedCoupon);
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(savedCoupon.getId()).orElseThrow();

            // assert
            assertThat(reloadedCoupon.getMinOrderAmount()).isZero();
        }
    }

    @DisplayName("회원·템플릿 발급 이력을 확인할 때,")
    @Nested
    class ExistsByUserIdAndCouponId {

        @DisplayName("같은 회원이 같은 템플릿에서 발급받은 이력이 있으면 true를 반환한다.")
        @Test
        void returnsTrue_whenAlreadyIssued() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertThat(userCouponRepository.existsByUserIdAndCouponId(100L, 1L)).isTrue();
        }

        @DisplayName("발급 이력이 없으면 false를 반환한다(다른 회원·다른 템플릿 포함).")
        @Test
        void returnsFalse_whenNotIssued() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertAll(
                () -> assertThat(userCouponRepository.existsByUserIdAndCouponId(200L, 1L)).isFalse(),
                () -> assertThat(userCouponRepository.existsByUserIdAndCouponId(100L, 2L)).isFalse()
            );
        }
    }

    @DisplayName("회원의 발급 쿠폰 전체를 조회할 때,")
    @Nested
    class FindByUserIdOrderByCreatedAtDesc {

        @DisplayName("본인 발급분만 발급 시각 내림차순으로 반환하고 타 회원 발급분은 제외한다.")
        @Test
        void returnsOwnCoupons_sortedByCreatedAtDesc_excludingOthers() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(2L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(200L, coupon(3L, 10_000)));

            // act
            List<UserCouponModel> userCoupons = userCouponRepository.findByUserIdOrderByCreatedAtDesc(100L);

            // assert
            assertAll(
                () -> assertThat(userCoupons).hasSize(2),
                () -> assertThat(userCoupons)
                    .extracting(UserCouponModel::getCouponId)
                    .containsExactlyInAnyOrder(1L, 2L),
                () -> assertThat(userCoupons)
                    .extracting(UserCouponModel::getCreatedAt)
                    .isSortedAccordingTo(Comparator.reverseOrder())
            );
        }

        @DisplayName("템플릿이 삭제된 발급 쿠폰도 포함된다.")
        @Test
        void includesCoupon_whenTemplateIsDeleted() {
            // arrange (템플릿을 저장·발급 후 템플릿만 soft delete)
            CouponModel savedCoupon = couponJpaRepository.save(coupon(null, 10_000));
            userCouponRepository.save(UserCouponModel.issue(100L, savedCoupon));
            savedCoupon.delete();
            couponJpaRepository.saveAndFlush(savedCoupon);

            // act
            List<UserCouponModel> userCoupons = userCouponRepository.findByUserIdOrderByCreatedAtDesc(100L);

            // assert
            assertThat(userCoupons)
                .extracting(UserCouponModel::getCouponId)
                .contains(savedCoupon.getId());
        }

        @DisplayName("발급 이력이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoCouponIssued() {
            // act & assert
            assertThat(userCouponRepository.findByUserIdOrderByCreatedAtDesc(100L)).isEmpty();
        }
    }

    @DisplayName("템플릿의 발급 내역을 페이지로 조회할 때,")
    @Nested
    class FindByCouponIdOrderByCreatedAtDesc {

        @DisplayName("해당 템플릿 발급분만 발급 시각 내림차순으로 페이징하고 타 템플릿 발급분은 제외한다.")
        @Test
        void returnsIssuedPage_sortedByCreatedAtDesc_excludingOtherTemplates() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(200L, coupon(1L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(300L, coupon(2L, 10_000)));

            // act
            Page<UserCouponModel> issuedPage = userCouponRepository.findByCouponIdOrderByCreatedAtDesc(1L, 0, 10);

            // assert
            assertAll(
                () -> assertThat(issuedPage.getTotalElements()).isEqualTo(2),
                () -> assertThat(issuedPage.getContent())
                    .extracting(UserCouponModel::getUserId)
                    .containsExactlyInAnyOrder(100L, 200L),
                () -> assertThat(issuedPage.getContent())
                    .extracting(UserCouponModel::getCreatedAt)
                    .isSortedAccordingTo(Comparator.reverseOrder())
            );
        }

        @DisplayName("2페이지를 요청하면, 오프셋이 적용되어 해당 페이지 항목만 반환된다.")
        @Test
        void returnsSecondPage_withOffset() {
            // arrange
            userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(200L, coupon(1L, 10_000)));
            userCouponRepository.save(UserCouponModel.issue(300L, coupon(1L, 10_000)));

            // act
            Page<UserCouponModel> firstPage = userCouponRepository.findByCouponIdOrderByCreatedAtDesc(1L, 0, 2);
            Page<UserCouponModel> secondPage = userCouponRepository.findByCouponIdOrderByCreatedAtDesc(1L, 1, 2);

            // assert
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getContent()).hasSize(2),
                () -> assertThat(secondPage.getContent()).hasSize(1)
            );
        }
    }

    @DisplayName("본인 소유 발급 쿠폰을 조회할 때,")
    @Nested
    class GetActiveByIdAndUserId {

        @DisplayName("본인 소유면 해당 발급 쿠폰을 반환한다.")
        @Test
        void returnsCoupon_whenOwnedByUser() {
            // arrange
            UserCouponModel savedCoupon = userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertThat(userCouponRepository.getActiveByIdAndUserId(savedCoupon.getId(), 100L).getId())
                .isEqualTo(savedCoupon.getId());
        }

        @DisplayName("타 회원 소유거나 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOwnedByOtherOrAbsent() {
            // arrange
            UserCouponModel savedCoupon = userCouponRepository.save(UserCouponModel.issue(100L, coupon(1L, 10_000)));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> userCouponRepository.getActiveByIdAndUserId(savedCoupon.getId(), 200L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThatThrownBy(() -> userCouponRepository.getActiveByIdAndUserId(-1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND)
            );
        }
    }
}
