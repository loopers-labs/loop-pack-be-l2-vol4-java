package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final int USERS = 50;
    private Long productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        productId = productRepository.save(
                new ProductModel(brand.getId(), "인기상품", null, Money.of(1000L), Quantity.of(100), null)
        ).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서로 다른 50명이 같은 상품에 동시에 좋아요하면, 좋아요 수는 정확히 50이어야 한다.")
    @Test
    void concurrentLikes_countExactly() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(USERS);

        for (int i = 0; i < USERS; i++) {
            final Long userId = (long) i;   // 서로 다른 유저
            executor.submit(() -> {
                try {
                    likeFacade.like(userId, productId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        ProductModel product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getLikeCount()).isEqualTo(USERS);   // ★ read-modify-write 면 깨질 지점
    }
}
