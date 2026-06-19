package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 10;
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 상품에 여러 사용자가 동시에 좋아요를 요청해도, 상품의 좋아요 수가 정상 반영된다.")
    @Test
    void reflectsAllLikes_whenMultipleUsersConcurrentlyLikeSameProduct() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "상품", BigDecimal.valueOf(10000)));
        Long productId = product.getId();

        List<String> loginIds = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            String loginId = String.format("likeuser%02d", i);
            userRepository.save(new UserModel(
                    loginId, LOGIN_PW, "유저" + i, "1990-01-01",
                    "likeuser" + i + "@example.com", Gender.MALE, passwordEncryptor));
            loginIds.add(loginId);
        }

        // when
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (String loginId : loginIds) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.like(loginId, LOGIN_PW, productId);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        done.await();
        executor.shutdown();

        // then
        Long likeCount = productRepository.find(productId).orElseThrow().getLikeCount();
        assertThat(likeCount).isEqualTo((long) THREAD_COUNT);
    }

    @DisplayName("동일한 상품에 여러 사용자가 동시에 싫어요(좋아요 취소)를 요청해도, 상품의 좋아요 수가 정상 반영된다.")
    @Test
    void reflectsAllUnlikes_whenMultipleUsersConcurrentlyUnlikeSameProduct() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "상품", BigDecimal.valueOf(10000)));
        Long productId = product.getId();

        List<String> loginIds = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            String loginId = String.format("likeuser%02d", i);
            userRepository.save(new UserModel(
                    loginId, LOGIN_PW, "유저" + i, "1990-01-01",
                    "likeuser" + i + "@example.com", Gender.MALE, passwordEncryptor));
            loginIds.add(loginId);
        }

        // 동시 취소 전 모든 유저가 좋아요한 상태로 준비
        for (String loginId : loginIds) {
            likeFacade.like(loginId, LOGIN_PW, productId);
        }

        // when
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (String loginId : loginIds) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.unlike(loginId, LOGIN_PW, productId);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        done.await();
        executor.shutdown();

        // then
        Long likeCount = productRepository.find(productId).orElseThrow().getLikeCount();
        assertThat(likeCount).isEqualTo(0L);
    }
}
