package com.loopers.domain.coupon;

import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("정상 쿠폰 ID로 요청하면 AVAILABLE 상태로 발급된다.")
        @Test
        void issuesCoupon_whenValidCouponIdIsProvided() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("신규 가입 10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));

            // act
            UserCouponModel result = couponService.issueCoupon(user.getId(), coupon.getId());

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getUserId()).isEqualTo(user.getId()),
                () -> assertThat(result.getCouponId()).isEqualTo(coupon.getId()),
                () -> assertThat(result.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 couponId로 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            Long nonExistentCouponId = 999L;

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(user.getId(), nonExistentCouponId)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰을 발급 시도하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(
                new CouponModel("만료된 쿠폰", CouponType.FIXED, 5000L, null, ZonedDateTime.now().minusDays(1))
            );
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(user.getId(), coupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("동일 쿠폰을 중복 발급 시도하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponAlreadyIssued() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5,000원 즉시 할인", CouponType.FIXED, 5000L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            couponService.issueCoupon(user.getId(), coupon.getId());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(user.getId(), coupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시,")
    @Nested
    class GetUserCoupons {

        @DisplayName("보유한 쿠폰이 있으면 전체 목록이 반환된다.")
        @Test
        void returnsAllCoupons_whenUserHasCoupons() {
            // arrange
            CouponModel coupon1 = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            CouponModel coupon2 = couponJpaRepository.save(new CouponModel("5,000원 할인", CouponType.FIXED, 5000L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            couponService.issueCoupon(user.getId(), coupon1.getId());
            couponService.issueCoupon(user.getId(), coupon2.getId());

            // act
            List<UserCouponModel> result = couponService.getUserCoupons(user.getId());

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("보유한 쿠폰이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenUserHasNoCoupons() {
            // arrange
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));

            // act
            List<UserCouponModel> result = couponService.getUserCoupons(user.getId());

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("본인의 쿠폰만 반환된다.")
        @Test
        void returnsOnlyOwnCoupons() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserModel user1 = userJpaRepository.save(new UserModel("user1", "pw1"));
            UserModel user2 = userJpaRepository.save(new UserModel("user2", "pw2"));
            couponService.issueCoupon(user1.getId(), coupon.getId());

            // act
            List<UserCouponModel> result = couponService.getUserCoupons(user2.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰 사용 시,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 본인 쿠폰을 사용하면 USED 상태로 변경된다.")
        @Test
        void usesCoupon_whenCouponIsAvailable() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            UserCouponModel issued = couponService.issueCoupon(user.getId(), coupon.getId());

            // act
            UserCouponModel result = couponService.useCoupon(user.getId(), issued.getId());

            // assert
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(userCouponJpaRepository.findById(issued.getId()).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("이미 사용한 쿠폰을 다시 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            UserCouponModel issued = couponService.issueCoupon(user.getId(), coupon.getId());
            couponService.useCoupon(user.getId(), issued.getId());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(user.getId(), issued.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("동일한 쿠폰을 동시에 사용 요청해도 낙관적 락으로 단 한 번만 사용된다.")
        @Test
        void usesCouponOnlyOnce_whenRequestedConcurrently() throws InterruptedException {
            // arrange
            CouponModel coupon = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
            UserCouponModel issued = couponService.issueCoupon(user.getId(), coupon.getId());

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        couponService.useCoupon(user.getId(), issued.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failureCount.get()).isEqualTo(threadCount - 1),
                () -> assertThat(userCouponJpaRepository.findById(issued.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserCouponStatus.USED)
            );
        }
    }
}
