package com.loopers.application.event;

import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(ApplicationEventPhaseTest.RecordingListener.class)
class ApplicationEventPhaseTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private RecordingListener listener;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void reset() {
        listener.afterCommitCount.set(0);
        listener.throwOnHandle = false;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("트랜잭션이 커밋되면, AFTER_COMMIT 리스너가 발화한다.")
    @Test
    void firesAfterCommit() {
        // when
        likeService.like(1L, 100L);

        // then
        assertThat(listener.afterCommitCount).hasValue(1);
    }

    @DisplayName("트랜잭션이 롤백되면, AFTER_COMMIT 리스너는 발화하지 않는다.")
    @Test
    void doesNotFireOnRollback() {
        // given
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // when
        catchThrowable(() -> tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(LikeChangedEvent.liked(1L, 100L));
            throw new RuntimeException("force rollback");
        }));

        // then
        assertThat(listener.afterCommitCount).hasValue(0);
    }

    @DisplayName("AFTER_COMMIT 리스너가 실패해도, 핵심 트랜잭션(좋아요)은 이미 커밋되어 유지된다.")
    @Test
    void coreCommitSurvivesListenerFailure() {
        // given
        Long productId = productRepository.save(ProductModel.of(
                1L,
                ProductName.of("티셔츠"),
                ProductDescription.of("면 100%"),
                ProductPrice.of(10000L)
        )).getId();
        listener.throwOnHandle = true;

        // when
        catchThrowable(() -> likeService.like(1L, productId));

        // then
        assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(1L);
    }

    @TestConfiguration
    static class RecordingListener {
        final AtomicInteger afterCommitCount = new AtomicInteger();
        volatile boolean throwOnHandle = false;

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onLikeChanged(LikeChangedEvent event) {
            afterCommitCount.incrementAndGet();
            if (throwOnHandle) {
                throw new RuntimeException("listener failure");
            }
        }
    }
}