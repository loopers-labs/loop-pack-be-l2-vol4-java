package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.application.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
    @DisplayName("?숈씪 ?ъ슜?먭? ?숈떆??醫뗭븘?붾? ?꾨Ⅴ硫?1嫄대쭔 ??λ맂??")
    void addLike_SameUserConcurrent_ShouldSaveOnce() throws InterruptedException, ExecutionException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000")));
        Long productId = product.getId();

        Long userId = 1L;
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<?>> futures = new ArrayList<>();
        
        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await(); // 10媛쒖쓽 ?ㅻ젅?쒓? 紐⑤몢 以鍮꾨맆 ?뚭퉴吏 ?湲????쇱젣??異쒕컻
                        likeFacade.addLike(userId, productId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            doneLatch.await(); // 紐⑤뱺 ?ㅻ젅?쒖쓽 ?묒뾽???앸궇 ?뚭퉴吏 ?湲?

            // then(?ㅻ젅???덉쇅 ?꾪뙆 蹂댁옣)
            for (Future<?> f : futures) {
                f.get();
            }

            assertThat(likeRepository.countByProductId(productId)).isEqualTo(1);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("10紐낆쓽 ?ъ슜?먭? ?숈떆??醫뗭븘?붾? ?꾨Ⅴ硫????놁씠 10媛쒖쓽 ?대젰???뺥솗??異붽??쒕떎.")
    void addLike_Concurrent10Requests_ShouldSaveAll() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000")));
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        likeFacade.addLike(userId, productId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            int likeCount = likeRepository.countByProductId(productId);
            assertThat(likeCount).isEqualTo(threadCount);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("醫뗭븘?붾? ?꾨Ⅸ 10紐낆쓽 ?ъ슜?먭? ?숈떆??痍⑥냼瑜??붿껌?섎㈃ ???놁씠 0媛쒕줈 ?덉쟾?섍쾶 吏?뚯쭊??")
    void removeLike_Concurrent10Requests_ShouldRemoveAll() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000")));
        Long productId = product.getId();

        // 10紐?誘몃━ 醫뗭븘??泥섎━
        for (int i = 0; i < 10; i++) {
            likeFacade.addLike((long) (i + 1), productId);
        }
        int initialLikeCount = likeRepository.countByProductId(productId);
        assertThat(initialLikeCount).isEqualTo(10);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        likeFacade.removeLike(userId, productId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            int likeCount = likeRepository.countByProductId(productId);
            assertThat(likeCount).isEqualTo(0);
        } finally {
            executorService.shutdown();
        }
    }
}
