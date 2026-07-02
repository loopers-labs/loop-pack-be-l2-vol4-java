package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.like.event.LikeAdded;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeEventIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private LikeCountRepository likeCountRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", "운동화", 1000L, 10));
        this.productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private long currentCount() {
        return likeCountRepository.find(productId).map(ProductLikeCount::getCount).orElse(0L);
    }

    @DisplayName("커밋된 트랜잭션에서 LikeAdded 를 발행하면, 동기 AFTER_COMMIT 리스너가 likeCount 를 증가시킨다.")
    @Test
    void afterCommit_increments() {
        tx.executeWithoutResult(s ->
            eventPublisher.publishEvent(new LikeAdded(7L, productId, ZonedDateTime.now())));

        // 동기 AFTER_COMMIT 이라 tx 반환 시점엔 이미 반영됨
        assertThat(currentCount()).isEqualTo(1L);
    }

    @DisplayName("롤백된 트랜잭션에서 LikeAdded 를 발행하면, AFTER_COMMIT 리스너는 실행되지 않는다.")
    @Test
    void rollback_skipsListener() {
        tx.executeWithoutResult(s -> {
            eventPublisher.publishEvent(new LikeAdded(7L, productId, ZonedDateTime.now()));
            s.setRollbackOnly();
        });

        assertThat(currentCount()).isEqualTo(0L);
    }
}
