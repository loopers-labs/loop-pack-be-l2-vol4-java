package com.loopers.concurrency;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "락 획득 순서가 엇갈리면 데드락, 같으면 안 난다" 를 직접 재현하는 학습용 실험 테스트.
 *
 * 핵심: 잠그는 행 집합({P_low, P_high})은 두 시나리오가 동일하다. 다른 건 "획득 순서"뿐.
 * 단일 IN 쿼리로는 InnoDB 가 알아서 PK 순서로 잠가 데드락이 안 나므로,
 * 여기서는 일부러 행을 "하나씩" 반대 순서로 잠가 순환 대기를 만든다.
 */
@DisplayName("비관적 락 — 락 획득 순서와 데드락")
@SpringBootTest
class LockOrderingDeadlockTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long lowId;
    private Long highId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        Long a = productJpaRepository.save(Product.create(brandId, "상품1", Money.of(1_000L))).getId();
        Long b = productJpaRepository.save(Product.create(brandId, "상품2", Money.of(1_000L))).getId();
        lowId = Math.min(a, b);
        highId = Math.max(a, b);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("두 트랜잭션이 같은 두 행을 반대 순서로 잠그면 데드락이 발생한다 (한쪽이 victim 으로 롤백).")
    @Test
    void provokesDeadlock_whenLockOrderDiffers() throws Exception {
        // 두 스레드가 각자 첫 락을 잡은 뒤에야 두 번째 락을 시도하도록 동기화 —
        // 그래야 "서로 상대가 쥔 행을 기다리는" 순환이 만들어진다.
        CountDownLatch bothHoldFirstLock = new CountDownLatch(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        // T1: low → high,  T2: high → low  (반대 순서)
        Future<Throwable> t1 = executor.submit(() -> lockTwoRows(lowId, highId, bothHoldFirstLock));
        Future<Throwable> t2 = executor.submit(() -> lockTwoRows(highId, lowId, bothHoldFirstLock));

        Throwable r1 = t1.get(30, TimeUnit.SECONDS);
        Throwable r2 = t2.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        // 둘 중 정확히 한쪽이 데드락 victim 으로 롤백되어야 한다.
        List<Throwable> failures = java.util.stream.Stream.of(r1, r2)
                .filter(t -> t != null).toList();

        assertThat(failures)
                .as("데드락으로 한쪽이 실패해야 한다")
                .hasSize(1);
        // raw EntityManager 사용이라 Spring 의 DAO 예외 변환이 걸리지 않는다 —
        // Hibernate 네이티브 예외(OptimisticLockException ← LockAcquisitionException)의 원인 체인으로 데드락을 판정한다.
        assertThat(isDeadlock(failures.get(0)))
                .as("실패 원인은 데드락이어야 한다")
                .isTrue();
    }

    private boolean isDeadlock(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.LockAcquisitionException
                    || (t.getMessage() != null && t.getMessage().contains("Deadlock"))) {
                return true;
            }
        }
        return false;
    }

    @DisplayName("두 트랜잭션이 같은 순서(낮은 id → 높은 id)로 잠그면 데드락 없이 직렬화된다.")
    @Test
    void noDeadlock_whenLockOrderConsistent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // 둘 다 low → high (같은 순서) — 중간 동기화 없이 자연 직렬화
        Future<Throwable> t1 = executor.submit(() -> lockTwoRows(lowId, highId, null));
        Future<Throwable> t2 = executor.submit(() -> lockTwoRows(lowId, highId, null));

        Throwable r1 = t1.get(30, TimeUnit.SECONDS);
        Throwable r2 = t2.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(r1).as("같은 순서면 데드락 없음").isNull();
        assertThat(r2).as("같은 순서면 데드락 없음").isNull();
    }

    /**
     * 한 트랜잭션에서 두 행을 firstId → secondId 순서로 비관적 쓰기락(FOR UPDATE)으로 잠근다.
     * midGate 가 주어지면, 첫 락 획득 후 양쪽이 모두 첫 락을 쥘 때까지 기다렸다가 두 번째 락을 시도한다.
     * 정상 완료면 null, 예외(데드락 등)면 그 Throwable 을 반환한다.
     */
    private Throwable lockTwoRows(Long firstId, Long secondId, CountDownLatch midGate) {
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                em.find(Product.class, firstId, LockModeType.PESSIMISTIC_WRITE); // 첫 행 잠금
                if (midGate != null) {
                    midGate.countDown();
                    awaitQuietly(midGate);
                }
                em.find(Product.class, secondId, LockModeType.PESSIMISTIC_WRITE); // 둘째 행 잠금 시도
            });
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
