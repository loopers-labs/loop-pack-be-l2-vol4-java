package com.loopers.application.event;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이벤트 리스너의 트랜잭션 phase 결합을 못박는 테스트.
 *
 * <p>부가 로직({@code LikeCountEventListener} 의 집계)은 {@code @TransactionalEventListener(AFTER_COMMIT)} 라
 * <b>발행 트랜잭션이 커밋됐을 때만</b> 발화해야 한다. 이것이 "집계 실패 ≠ 좋아요 성공"(부가는 주요를 되돌리지 못함)과
 * "롤백된 행동은 부가효과도 없어야 함"(유령 반영 방지)을 동시에 보장한다.</p>
 *
 * <p>트랜잭션 경계를 통제하려고 {@link TxHarness} 로 같은 이벤트를 (a) 커밋 (b) 롤백 상황에서 발행하고,
 * 실제 리스너가 건드리는 {@code Product.likeCount} 로 발화 여부를 관찰한다.</p>
 */
@SpringBootTest
class EventPhaseIntegrationTest {

    @Autowired
    private TxHarness txHarness;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(Product.create(brandId, "상품1", Money.of(1_000L))).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("발행 트랜잭션이 커밋되면 AFTER_COMMIT 집계 리스너가 발화해 likeCount 가 반영된다.")
    @Test
    void afterCommitListener_firesOnCommit() {
        txHarness.publishThenCommit(new ProductLikedEvent(1L, productId, ZonedDateTime.now()));

        // 집계 리스너는 @Async 라 eventual → 1 로 수렴할 때까지 대기(수렴 = 발화 증명).
        long counter = awaitLikeCount(1L);
        assertThat(counter).as("커밋되면 리스너가 발화해 likeCount 가 1 이 되어야 한다").isEqualTo(1L);
    }

    @DisplayName("발행 트랜잭션이 롤백되면 AFTER_COMMIT 리스너가 발화하지 않아 likeCount 가 그대로다.")
    @Test
    void afterCommitListener_doesNotFireOnRollback() {
        assertThatThrownBy(() ->
                txHarness.publishThenRollback(new ProductLikedEvent(1L, productId, ZonedDateTime.now())))
                .isInstanceOf(IllegalStateException.class);

        // 롤백이면 afterCommit 콜백이 호출되지 않아 리스너가 아예 실행되지 않는다(async 디스패치조차 없음).
        // 혹시 모를 비동기 발화를 배제하려 잠시 대기한 뒤에도 0 이어야 한다.
        sleepQuietly(500);
        long counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();
        assertThat(counter).as("롤백되면 리스너가 발화하지 않아 likeCount 는 0 이어야 한다").isZero();
    }

    private long awaitLikeCount(long expected) {
        long counter = -1L;
        for (int i = 0; i < 100; i++) {
            counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();
            if (counter == expected) {
                return counter;
            }
            sleepQuietly(100);
        }
        return counter;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @TestConfiguration
    static class TxHarnessConfig {
        @Bean
        TxHarness txHarness(ApplicationEventPublisher publisher) {
            return new TxHarness(publisher);
        }
    }

    /**
     * 이벤트 발행을 트랜잭션 경계 안에서 통제하는 테스트 하네스. 별도 빈이라 프록시를 거쳐 {@code @Transactional} 이
     * 적용된다(자기호출 우회).
     */
    static class TxHarness {
        private final ApplicationEventPublisher publisher;

        TxHarness(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishThenCommit(Object event) {
            publisher.publishEvent(event);
        }

        @Transactional
        public void publishThenRollback(Object event) {
            publisher.publishEvent(event);
            throw new IllegalStateException("force rollback");
        }
    }
}
