package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponQuota;
import com.loopers.domain.coupon.CouponQuotaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponQuotaConcurrencyTest {

    private final CouponQuotaRepository couponQuotaRepository;
    private final CouponQuotaJpaRepository couponQuotaJpaRepository;
    private final TransactionTemplate tx;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponQuotaConcurrencyTest(
        CouponQuotaRepository couponQuotaRepository,
        CouponQuotaJpaRepository couponQuotaJpaRepository,
        PlatformTransactionManager transactionManager,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponQuotaRepository = couponQuotaRepository;
        this.couponQuotaJpaRepository = couponQuotaJpaRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("한도 100 쿠폰에 300개의 발급이 동시에 몰려도, 정확히 100개만 발급되고 한도 초과는 발생하지 않는다.")
    @Test
    void issuesExactlyUpToLimit_underConcurrency() throws Exception {
        // given
        int limit = 100;
        int attempts = 300;
        CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota((long) limit));
        Long couponPolicyId = quota.getId();

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        // when : 모든 스레드가 startGate 에서 대기하다 동시에 출발해 같은 쿠폰을 경합한다
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return tx.execute(status -> couponQuotaRepository.increaseIssued(couponPolicyId));
            }));
        }
        startGate.countDown();

        int issued = 0;
        for (Future<Integer> future : futures) {
            issued += future.get();
        }
        pool.shutdown();

        // then
        int totalIssued = issued;
        assertAll(
            () -> assertThat(totalIssued).isEqualTo(limit),                                  // 성공(affected=1)은 정확히 100
            () -> assertThat(reloadIssuedCount(couponPolicyId)).isEqualTo((long) limit)       // 누적 발급수도 100 — 초과 0
        );
    }

    private long reloadIssuedCount(Long id) {
        return couponQuotaJpaRepository.findById(id).orElseThrow().getIssuedCount();
    }
}
