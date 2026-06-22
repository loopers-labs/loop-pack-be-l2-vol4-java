package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
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
@DisplayName("재고 동시성 테스트")
class StockConcurrencyE2ETest {

    private static final String ORDER_URL = "/api/v1/orders";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        savedProduct = productJpaRepository.save(new ProductModel("에어포스1", 10000L, 1L));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("C-1: 재고 1개 상품에 2명이 동시에 주문하면 1건만 성공하고 1건은 실패한다.")
    @Test
    void stock1_2concurrent_only1succeeds() throws Exception {
        // arrange
        stockJpaRepository.save(new StockModel(savedProduct.getId(), 1));

        UserModel user1 = userService.signUp(new UserModel("user01", "Password1!", "유저1", LocalDate.of(1990, 1, 1), "u1@test.com"));
        UserModel user2 = userService.signUp(new UserModel("user02", "Password1!", "유저2", LocalDate.of(1990, 1, 1), "u2@test.com"));

        HttpHeaders headers1 = headers("user01");
        HttpHeaders headers2 = headers("user02");
        OrderDto.CreateRequest request = orderRequest(savedProduct.getId(), 1);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // act
        Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>> f1 = executor.submit(() -> {
            latch.await();
            return placeOrder(headers1, request);
        });
        Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>> f2 = executor.submit(() -> {
            latch.await();
            return placeOrder(headers2, request);
        });

        latch.countDown();
        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> r1 = f1.get();
        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> r2 = f2.get();
        executor.shutdown();

        // assert
        List<HttpStatus> statuses = List.of(
            (HttpStatus) r1.getStatusCode(),
            (HttpStatus) r2.getStatusCode()
        );
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
        assertThat(stockJpaRepository.findByProductId(savedProduct.getId()).orElseThrow().getQuantity()).isEqualTo(0);
    }

    @DisplayName("C-2: 재고 5개 상품에 10명이 동시에 주문하면 5건만 성공하고 최종 재고는 0이다.")
    @Test
    void stock5_10concurrent_5succeed() throws Exception {
        // arrange
        stockJpaRepository.save(new StockModel(savedProduct.getId(), 5));

        List<HttpHeaders> headersList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String loginId = "user" + String.format("%02d", i);
            userService.signUp(new UserModel(loginId, "Password1!", "유저" + i, LocalDate.of(1990, 1, 1), "u" + i + "@test.com"));
            headersList.add(headers(loginId));
        }
        OrderDto.CreateRequest request = orderRequest(savedProduct.getId(), 1);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // act
        List<Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>>> futures = new ArrayList<>();
        for (HttpHeaders h : headersList) {
            futures.add(executor.submit(() -> {
                latch.await();
                return placeOrder(h, request);
            }));
        }

        latch.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>> f : futures) {
            statuses.add((HttpStatus) f.get().getStatusCode());
        }
        executor.shutdown();

        // assert
        long successCount = statuses.stream().filter(s -> s == HttpStatus.CREATED).count();
        long failCount = statuses.stream().filter(s -> s == HttpStatus.BAD_REQUEST).count();
        assertThat(successCount).isEqualTo(5);
        assertThat(failCount).isEqualTo(5);
        assertThat(stockJpaRepository.findByProductId(savedProduct.getId()).orElseThrow().getQuantity()).isEqualTo(0);
    }

    @DisplayName("C-3: 재고 10개 상품에 10명이 동시에 주문하면 모두 성공하고 최종 재고는 0이다.")
    @Test
    void stock10_10concurrent_allSucceed() throws Exception {
        // arrange
        stockJpaRepository.save(new StockModel(savedProduct.getId(), 10));

        List<HttpHeaders> headersList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String loginId = "user" + String.format("%02d", i);
            userService.signUp(new UserModel(loginId, "Password1!", "유저" + i, LocalDate.of(1990, 1, 1), "u" + i + "@test.com"));
            headersList.add(headers(loginId));
        }
        OrderDto.CreateRequest request = orderRequest(savedProduct.getId(), 1);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // act
        List<Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>>> futures = new ArrayList<>();
        for (HttpHeaders h : headersList) {
            futures.add(executor.submit(() -> {
                latch.await();
                return placeOrder(h, request);
            }));
        }

        latch.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<ResponseEntity<ApiResponse<OrderDto.OrderResponse>>> f : futures) {
            statuses.add((HttpStatus) f.get().getStatusCode());
        }
        executor.shutdown();

        // assert
        assertThat(statuses).allMatch(s -> s == HttpStatus.CREATED);
        assertThat(stockJpaRepository.findByProductId(savedProduct.getId()).orElseThrow().getQuantity()).isEqualTo(0);
    }

    private ResponseEntity<ApiResponse<OrderDto.OrderResponse>> placeOrder(HttpHeaders headers, OrderDto.CreateRequest request) {
        return testRestTemplate.exchange(
            ORDER_URL, HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders headers(String loginId) {
        HttpHeaders h = new HttpHeaders();
        h.set(LOGIN_ID_HEADER, loginId);
        h.set(LOGIN_PW_HEADER, "Password1!");
        return h;
    }

    private OrderDto.CreateRequest orderRequest(Long productId, int quantity) {
        return new OrderDto.CreateRequest(List.of(new OrderDto.OrderItemRequest(productId, quantity)), null);
    }
}
