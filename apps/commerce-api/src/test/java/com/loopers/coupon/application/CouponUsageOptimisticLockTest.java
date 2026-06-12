package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CouponUsageOptimisticLockTest {

    @Autowired private CouponUsageService couponUsageService;
    @Autowired private CouponAdminService couponAdminService;
    @Autowired private CouponIssueService couponIssueService;
    @Autowired private UserAccountService userAccountService;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long userCouponId;

    @BeforeEach
    void setUp() {
        userId = userAccountService.signUp(new UserCommand.SignUp(
                "loopers01", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        )).id();
        Long couponId = couponAdminService.create(new CouponCommand.Create(
                "3천원 할인", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30)
        )).id();
        userCouponId = couponIssueService.issue(userId, couponId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("다른 트랜잭션이 먼저 사용을 커밋한 쿠폰을 과거 상태(1차 캐시)로 다시 사용하면 이미 사용된 쿠폰 충돌이 발생한다")
    void givenCouponUsedByOtherTx_whenUseWithStaleEntity_thenThrowsAlreadyUsedConflict() {
        transactionTemplate.executeWithoutResult(status -> {
            // 현재 트랜잭션의 1차 캐시에 AVAILABLE 상태 스냅샷을 적재해 둔다
            userCouponJpaRepository.findById(userCouponId).orElseThrow();

            // 다른 트랜잭션이 같은 쿠폰 사용을 먼저 커밋한다
            CompletableFuture.runAsync(
                    () -> couponUsageService.use(userCouponId, userId, 10_000L)
            ).join();

            // 과거 상태로 다시 사용을 시도하면 덮어쓰지 않고 충돌로 거부돼야 한다
            assertThatThrownBy(() -> couponUsageService.use(userCouponId, userId, 10_000L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_ALREADY_USED);

            status.setRollbackOnly();
        });

        CouponStatus finalStatus = userCouponJpaRepository.findById(userCouponId).orElseThrow().getStatus();
        assertThat(finalStatus).isEqualTo(CouponStatus.USED);
    }
}
