package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponQuota;
import com.loopers.domain.coupon.CouponQuotaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponQuotaRepositoryIntegrationTest {

    private final CouponQuotaRepository couponQuotaRepository;
    private final CouponQuotaJpaRepository couponQuotaJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final TransactionTemplate tx;

    @Autowired
    CouponQuotaRepositoryIntegrationTest(
        CouponQuotaRepository couponQuotaRepository,
        CouponQuotaJpaRepository couponQuotaJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        PlatformTransactionManager transactionManager
    ) {
        this.couponQuotaRepository = couponQuotaRepository;
        this.couponQuotaJpaRepository = couponQuotaJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("선착순 발급수를 원자적으로 차감할 때, ")
    @Nested
    class IncreaseIssued {

        @DisplayName("한도가 남아 있으면 발급수가 1 증가하고 1(영향 행 수)을 반환한다.")
        @Test
        void increasesAndReturnsOne_whenUnderLimit() {
            // given
            CouponQuota saved = couponQuotaJpaRepository.save(new CouponQuota(2L));

            // when
            int affected = issueOnce(saved.getId());

            // then
            assertAll(
                () -> assertThat(affected).isEqualTo(1),
                () -> assertThat(reload(saved.getId()).getIssuedCount()).isEqualTo(1L)
            );
        }

        @DisplayName("한도에 도달하면 더는 증가하지 않고 0(영향 행 없음)을 반환한다.")
        @Test
        void doesNotIncrease_whenLimitReached() {
            // given
            CouponQuota saved = couponQuotaJpaRepository.save(new CouponQuota(2L));
            issueOnce(saved.getId());
            issueOnce(saved.getId());

            // when : 한도(2)를 초과하는 3번째 발급
            int affected = issueOnce(saved.getId());

            // then
            assertAll(
                () -> assertThat(affected).isEqualTo(0),
                () -> assertThat(reload(saved.getId()).getIssuedCount()).isEqualTo(2L)
            );
        }

        @DisplayName("무제한(max_issue_count=null) 정책은 항상 발급수가 증가한다.")
        @Test
        void alwaysIncreases_whenUnlimited() {
            // given
            CouponQuota saved = couponQuotaJpaRepository.save(new CouponQuota(null));

            // when
            int affected = issueOnce(saved.getId());

            // then
            assertAll(
                () -> assertThat(affected).isEqualTo(1),
                () -> assertThat(reload(saved.getId()).getIssuedCount()).isEqualTo(1L)
            );
        }
    }

    /**
     * 호출자(브릭4 Facade)의 트랜잭션 경계를 테스트가 대역으로 흉내 낸다.
     * 벌크 @Modifying 은 트랜잭션이 필요하지만, reload 가 같은 트랜잭션에 묶이면 stale 을 읽으므로
     * 발급 호출만 트랜잭션으로 감싸 커밋시키고 reload 는 밖(새 트랜잭션)에서 fresh 로 읽는다.
     */
    private int issueOnce(Long couponPolicyId) {
        return tx.execute(status -> couponQuotaRepository.increaseIssued(couponPolicyId));
    }

    private CouponQuota reload(Long id) {
        return couponQuotaJpaRepository.findById(id).orElseThrow();
    }
}
