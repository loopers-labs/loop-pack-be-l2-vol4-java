package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponExpiry;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USERID   = "concurrencyUser";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private UserModel savedUser;
    private ProductStockModel savedStock;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name("동시성유저"),
                new BirthDay("1990-01-01"),
                new Email("concurrency@test.com"),
                UserRole.USER
        ));
        BrandModel brand = brandRepository.save(new BrandModel("동시성브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("동시성상품")));
        savedStock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", DEFAULT_USERID);
        headers.set("X-Loopers-LoginPw", DEFAULT_PASSWORD);
        return headers;
    }

    private String generateOrderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(ORDER_DATE_FORMATTER) + suffix;
    }

    private UserCouponModel saveFixedCoupon(long discountAmount) {
        CouponModel coupon = couponRepository.save(new CouponModel(
                "동시성테스트쿠폰",
                new CouponDiscount(CouponType.FIXED, discountAmount, null),
                new CouponExpiry(ZonedDateTime.now().plusDays(7))
        ));
        return userCouponRepository.save(new UserCouponModel(savedUser.getId(), coupon));
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> requestCreateOrder(int quantity) {
        OrderV1Dto.OrderRequest request = new OrderV1Dto.OrderRequest(
                generateOrderNumber(),
                List.of(new OrderV1Dto.OrderItemRequest(savedStock.getId(), quantity)),
                null
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), type);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> requestCreateOrderWithCoupon(int quantity, Long couponId) {
        OrderV1Dto.OrderRequest request = new OrderV1Dto.OrderRequest(
                generateOrderNumber(),
                List.of(new OrderV1Dto.OrderItemRequest(savedStock.getId(), quantity)),
                couponId
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), type);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> requestCancelOrder(Long orderId) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST, new HttpEntity<>(authHeaders()), type);
    }

    @DisplayName("POST /api/v1/orders - 동시성")
    @Nested
    class ConcurrentOrder {

        @DisplayName("재고 5개인 상품에 10건이 동시에 주문하면, 5건만 성공하고 재고는 0이 된다.")
        @Test
        void onlySucceedsUpToStock_whenConcurrentOrdersExceedStock() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return requestCreateOrder(1);
                }));
            }

            ready.await();
            start.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            long successCount = futures.stream()
                    .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                    .filter(r -> r.getStatusCode() == HttpStatus.CREATED)
                    .count();
            int finalStock = productStockRepository.findById(savedStock.getId()).get().getStockQuantity().getValue();

            assertThat(successCount).isEqualTo(5);
            assertThat(finalStock).isEqualTo(0);
        }
    }

    @DisplayName("POST /api/v1/orders - 쿠폰 동시 사용")
    @Nested
    class ConcurrentCouponOrder {

        @DisplayName("동일한 쿠폰으로 2건이 동시에 주문하면, 1건만 성공한다.")
        @Test
        void onlyOneOrderSucceeds_whenSameCouponUsedConcurrently() throws Exception {
            UserCouponModel userCoupon = saveFixedCoupon(1000L);

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return requestCreateOrderWithCoupon(1, userCoupon.getId());
                }));
            }

            ready.await();
            start.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            long successCount = futures.stream()
                    .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                    .filter(r -> r.getStatusCode() == HttpStatus.CREATED)
                    .count();

            assertThat(successCount).isEqualTo(1);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel - 동시성")
    @Nested
    class ConcurrentCancel {

        @DisplayName("동일한 주문을 동시에 2번 취소 요청하면, 1건만 성공하고 재고는 1번만 복구된다.")
        @Test
        void onlyOneCancelSucceeds_whenDuplicateCancelRequested() throws Exception {
            Long orderId = requestCreateOrder(2).getBody().data().id();
            int stockAfterOrder = productStockRepository.findById(savedStock.getId()).get().getStockQuantity().getValue();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return requestCancelOrder(orderId);
                }));
            }

            ready.await();
            start.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            long successCount = futures.stream()
                    .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                    .filter(r -> r.getStatusCode().is2xxSuccessful())
                    .count();
            int finalStock = productStockRepository.findById(savedStock.getId()).get().getStockQuantity().getValue();

            assertThat(successCount).isEqualTo(1);
            assertThat(finalStock).isEqualTo(stockAfterOrder + 2);
        }
    }
}
