package com.loopers.product.application.event;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductLikeCountReconcilerIntegrationTest {

    private final ProductLikeCountReconciler reconciler;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductLikeCountReconcilerIntegrationTest(
            ProductLikeCountReconciler reconciler,
            ProductRepository productRepository,
            LikeRepository likeRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.reconciler = reconciler;
        this.productRepository = productRepository;
        this.likeRepository = likeRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProductWithDriftedLikeCount(long drifted) {
        Product product = Product.create(1L, "상품", "설명", 1000L, null);
        ReflectionTestUtils.setField(product, "likeCount", drifted);
        return productRepository.save(product);
    }

    @Test
    @DisplayName("reconcile 은 드리프트된 like_count 를 SSOT 기준 활성 좋아요 수로 교정하며 취소 좋아요는 제외한다")
    void givenDriftedLikeCount_whenReconcile_thenResetToSsotActiveCount() {
        Product popular = saveProductWithDriftedLikeCount(100); // 실제 활성 2개
        likeRepository.save(Like.create(1L, popular.getId()));
        likeRepository.save(Like.create(2L, popular.getId()));
        Like cancelled = Like.create(3L, popular.getId());
        cancelled.delete();
        likeRepository.save(cancelled); // 취소 → 안 셈

        Product none = saveProductWithDriftedLikeCount(50); // 실제 활성 0개

        reconciler.reconcile();

        assertAll(
                () -> assertThat(productRepository.findById(popular.getId()).orElseThrow().getLikeCount()).isEqualTo(2L),
                () -> assertThat(productRepository.findById(none.getId()).orElseThrow().getLikeCount()).isEqualTo(0L)
        );
    }
}
