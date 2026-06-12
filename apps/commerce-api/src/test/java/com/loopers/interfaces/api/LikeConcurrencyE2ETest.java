package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("좋아요 동시성 테스트")
class LikeConcurrencyE2ETest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        savedProduct = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("C-3: 10명이 동시에 좋아요를 누르면 likeCount가 정확히 10이다.")
    @Test
    void concurrent10Likes_likeCountIs10() throws Exception {
        // arrange
        List<HttpHeaders> headersList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String loginId = "user" + String.format("%02d", i);
            userService.signUp(new UserModel(loginId, "Password1!", "유저" + i, LocalDate.of(1990, 1, 1), "u" + i + "@test.com"));
            headersList.add(headers(loginId));
        }

        String likeUrl = "/api/v1/products/" + savedProduct.getId() + "/likes";
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // act
        List<Future<ResponseEntity<Void>>> futures = new ArrayList<>();
        for (HttpHeaders h : headersList) {
            futures.add(executor.submit(() -> {
                latch.await();
                return testRestTemplate.exchange(likeUrl, HttpMethod.POST, new HttpEntity<>(h), Void.class);
            }));
        }

        latch.countDown();
        for (Future<ResponseEntity<Void>> f : futures) {
            f.get();
        }
        executor.shutdown();

        // assert
        ProductModel result = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(result.getLikeCount()).isEqualTo(10);
    }

    @DisplayName("C-4: 10명이 좋아요 후 동시에 취소하면 likeCount가 정확히 0이다.")
    @Test
    void concurrent10Unlikes_likeCountIs0() throws Exception {
        // arrange
        List<HttpHeaders> headersList = new ArrayList<>();
        String likeUrl = "/api/v1/products/" + savedProduct.getId() + "/likes";

        for (int i = 1; i <= 10; i++) {
            String loginId = "user" + String.format("%02d", i);
            userService.signUp(new UserModel(loginId, "Password1!", "유저" + i, LocalDate.of(1990, 1, 1), "u" + i + "@test.com"));
            HttpHeaders h = headers(loginId);
            headersList.add(h);
            // 순차 좋아요 (likeCount=10 보장)
            testRestTemplate.exchange(likeUrl, HttpMethod.POST, new HttpEntity<>(h), Void.class);
        }

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // act
        List<Future<ResponseEntity<Void>>> futures = new ArrayList<>();
        for (HttpHeaders h : headersList) {
            futures.add(executor.submit(() -> {
                latch.await();
                return testRestTemplate.exchange(likeUrl, HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
            }));
        }

        latch.countDown();
        for (Future<ResponseEntity<Void>> f : futures) {
            f.get();
        }
        executor.shutdown();

        // assert
        ProductModel result = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(result.getLikeCount()).isEqualTo(0);
    }

    private HttpHeaders headers(String loginId) {
        HttpHeaders h = new HttpHeaders();
        h.set(LOGIN_ID_HEADER, loginId);
        h.set(LOGIN_PW_HEADER, "Password1!");
        return h;
    }
}
