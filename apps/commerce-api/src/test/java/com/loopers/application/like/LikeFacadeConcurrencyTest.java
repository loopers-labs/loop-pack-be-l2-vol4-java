package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeFacadeConcurrencyTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("10명의 사용자가 동시에 좋아요를 누르면 락 없이 10개의 이력이 정확히 추가된다.")
    void addLike_Concurrent10Requests_ShouldSaveAll() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000")));
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    likeFacade.addLike(userId, productId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        int likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("좋아요를 누른 10명의 사용자가 동시에 취소를 요청하면 락 없이 0개로 안전하게 지워진다.")
    void removeLike_Concurrent10Requests_ShouldRemoveAll() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000")));
        Long productId = product.getId();

        // 10명 미리 좋아요 처리
        for (int i = 0; i < 10; i++) {
            likeFacade.addLike((long) (i + 1), productId);
        }
        int initialLikeCount = likeRepository.countByProductId(productId);
        assertThat(initialLikeCount).isEqualTo(10);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    likeFacade.removeLike(userId, productId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        int likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(0);
    }
}
