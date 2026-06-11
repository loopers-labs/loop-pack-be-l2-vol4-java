package com.loopers.concurrency;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.like.application.LikeReader;
import com.loopers.like.application.LikeService;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    private static final int USERS = 10;

    @Autowired private LikeService likeService;
    @Autowired private LikeReader likeReader;
    @Autowired private UserAccountService userAccountService;
    @Autowired private BrandAdminService brandAdminService;
    @Autowired private ProductAdminService productAdminService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, null, 50)).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long signUp(int idx) {
        return userAccountService.signUp(new UserCommand.SignUp(
                "looper" + idx, "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper" + idx + "@example.com"
        )).id();
    }

    private void runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();
    }

    @Test
    @DisplayName("여러 사용자가 같은 상품에 동시에 좋아요를 눌러도 좋아요 수가 정확히 반영된다")
    void givenManyUsers_whenConcurrentLike_thenCountIsExact() throws InterruptedException {
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            userIds.add(signUp(i));
        }

        List<Runnable> tasks = new ArrayList<>();
        for (Long userId : userIds) {
            tasks.add(() -> likeService.register(userId, productId));
        }
        runConcurrently(tasks);

        assertThat(likeReader.countActive(productId)).isEqualTo(USERS);
    }

    @Test
    @DisplayName("같은 사용자가 같은 상품에 동시에 여러 번 좋아요를 눌러도 좋아요는 한 번만 집계된다")
    void givenSameUser_whenConcurrentLike_thenCountedOnce() throws InterruptedException {
        Long userId = signUp(0);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            tasks.add(() -> likeService.register(userId, productId));
        }
        runConcurrently(tasks);

        assertThat(likeReader.countActive(productId)).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 사용자가 좋아요한 상태에서 동시에 취소해도 좋아요 수가 정확히 0으로 반영된다")
    void givenManyLikedUsers_whenConcurrentCancel_thenCountIsZero() throws InterruptedException {
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            Long userId = signUp(i);
            likeService.register(userId, productId);
            userIds.add(userId);
        }
        assertThat(likeReader.countActive(productId)).isEqualTo(USERS);

        List<Runnable> tasks = new ArrayList<>();
        for (Long userId : userIds) {
            tasks.add(() -> likeService.cancel(userId, productId));
        }
        runConcurrently(tasks);

        assertThat(likeReader.countActive(productId)).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 사용자가 같은 상품에 동시에 여러 번 좋아요를 취소해도 좋아요 수는 0으로 안전하게 반영된다")
    void givenSameUserLiked_whenConcurrentCancel_thenCountedZeroSafely() throws InterruptedException {
        Long userId = signUp(0);
        likeService.register(userId, productId);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            tasks.add(() -> likeService.cancel(userId, productId));
        }
        runConcurrently(tasks);

        assertThat(likeReader.countActive(productId)).isEqualTo(0);
    }
}
