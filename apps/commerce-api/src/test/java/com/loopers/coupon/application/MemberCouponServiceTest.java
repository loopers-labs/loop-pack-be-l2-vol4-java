package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponState;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.MemberCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MemberCouponServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private MemberCouponService memberCouponService;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private MemberCouponJpaRepository memberCouponJpaRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long memberId;
    private Long couponId;

    @BeforeEach
    void setUp() {
        memberId = 1L;
        Coupon coupon =
            couponJpaRepository.save(
                new Coupon(
                    "정액 3천원", CouponType.FIXED, 3_000L, null, ZonedDateTime.now(SEOUL).plusDays(1)));
        couponId = coupon.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** useForOrder 는 비관적 락 조회 + 변경 감지 저장을 사용하므로 호출자(Facade) 트랜잭션을 대신해 트랜잭션 안에서 실행한다. */
    private long useForOrder(Long memberCouponId, Long ownerId, long orderAmount, Long orderId) {
        return transactionTemplate.execute(
            status -> memberCouponService.useForOrder(memberCouponId, ownerId, orderAmount, orderId));
    }

    @DisplayName("쿠폰을 발급하면 AVAILABLE 상태로 저장된다.")
    @Test
    void issue() {
        MemberCoupon issued = memberCouponService.issue(memberId, couponId);
        assertThat(issued.getState()).isEqualTo(CouponState.AVAILABLE);
        assertThat(memberCouponService.getMyCoupons(memberId)).hasSize(1);
    }

    @DisplayName("주문에 쿠폰을 사용할 때,")
    @Nested
    class UseForOrder {
        @DisplayName("정상 사용 시 할인 금액을 반환하고 USED 로 전이된다.")
        @Test
        void usesAndReturnsDiscount() {
            MemberCoupon issued = memberCouponService.issue(memberId, couponId);

            long discount = useForOrder(issued.getId(), memberId, 10_000L, 500L);

            assertThat(discount).isEqualTo(3_000L);
            assertThat(memberCouponJpaRepository.findById(issued.getId()).orElseThrow().getState())
                .isEqualTo(CouponState.USED);
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result =
                assertThrows(CoreException.class, () -> useForOrder(999L, memberId, 10_000L, 500L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타 회원 소유 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            MemberCoupon issued = memberCouponService.issue(memberId, couponId);
            CoreException result =
                assertThrows(CoreException.class, () -> useForOrder(issued.getId(), 2L, 10_000L, 500L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            MemberCoupon issued = memberCouponService.issue(memberId, couponId);
            useForOrder(issued.getId(), memberId, 10_000L, 500L);

            CoreException result =
                assertThrows(
                    CoreException.class, () -> useForOrder(issued.getId(), memberId, 10_000L, 501L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
