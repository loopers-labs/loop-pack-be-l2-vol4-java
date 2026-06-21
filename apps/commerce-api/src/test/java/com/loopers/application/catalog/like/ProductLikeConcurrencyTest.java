package com.loopers.application.catalog.like;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductLikeConcurrencyTest {

    private final ProductLikeCommandService productLikeCommandService;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductLikeConcurrencyTest(
        ProductLikeCommandService productLikeCommandService,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productLikeCommandService = productLikeCommandService;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("여러 사용자가 동시에 좋아요를 등록하고 취소해도 상품 좋아요 수가 정상 반영된다.")
    @Test
    void reflectsLikeCount_whenLikesAndUnlikesRunConcurrently() throws Exception {
        Product product = saveProduct();
        int requestCount = 10;

        runConcurrently(requestCount, index -> productLikeCommandService.like(
            new ProductLikeCommand.Like("user" + index, product.getId())
        ));
        assertThat(productRepository.find(product.getId()).orElseThrow().getLikeCount()).isEqualTo(requestCount);

        runConcurrently(requestCount, index -> productLikeCommandService.unlike(
            new ProductLikeCommand.Unlike("user" + index, product.getId())
        ));
        assertThat(productRepository.find(product.getId()).orElseThrow().getLikeCount()).isZero();
    }

    private void runConcurrently(int requestCount, IndexedTask task) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                int index = i;
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    task.run(index);
                    return null;
                }));
            }
            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private Product saveProduct() {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L, 10));
    }

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index);
    }
}
