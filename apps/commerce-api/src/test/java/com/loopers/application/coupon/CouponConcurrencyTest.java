package com.loopers.application.coupon;

import com.loopers.application.ordering.order.OrderCommand;
import com.loopers.application.ordering.order.OrderFacade;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponConcurrencyTest {

    private final CouponCommandService couponCommandService;
    private final CouponQueryService couponQueryService;
    private final OrderFacade orderFacade;
    private final OrderRepository orderRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponConcurrencyTest(
        CouponCommandService couponCommandService,
        CouponQueryService couponQueryService,
        OrderFacade orderFacade,
        OrderRepository orderRepository,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponCommandService = couponCommandService;
        this.couponQueryService = couponQueryService;
        this.orderFacade = orderFacade;
        this.orderRepository = orderRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 사용자가 동시에 쿠폰을 발급받아도 템플릿의 사용자별 한도를 넘지 않는다.")
    @Test
    void preventsIssueLimitOverflow_whenSameUserIssuesConcurrently() throws Exception {
        Long couponTemplateId = createTemplate(CouponType.FIXED, 1_000L, 1);

        List<Boolean> results = runConcurrently(2, () -> {
            couponCommandService.issue(couponTemplateId, "user1");
            return true;
        });

        assertAll(
            () -> assertThat(results).containsExactlyInAnyOrder(true, false),
            () -> assertThat(couponQueryService.getMyCoupons("user1", 0, 20).totalElements()).isEqualTo(1L)
        );
    }

    @DisplayName("동일 발급 쿠폰으로 동시에 주문해도 한 주문만 성공하고 실패 주문의 재고 차감은 롤백한다.")
    @Test
    void usesIssuedCouponOnlyOnce_whenOrdersRequestConcurrently() throws Exception {
        Product product = saveProduct("쿠폰 락 테스트 상품", 2_000L, 2);
        Long issuedCouponId = couponCommandService.issue(
            createTemplate(CouponType.FIXED, 1_000L, 1),
            "user1"
        ).couponId();

        List<Boolean> results = runConcurrently(2, () -> {
            orderFacade.placeOrder(new OrderCommand.Create(
                "user1",
                List.of(new OrderCommand.Item(product.getId(), 1)),
                issuedCouponId
            ));
            return true;
        });

        Product changedProduct = productRepository.find(product.getId()).orElseThrow();
        CouponResult.Issued issuedCoupon = couponQueryService.getMyCoupons("user1", 0, 20).items().get(0);

        assertAll(
            () -> assertThat(results).containsExactlyInAnyOrder(true, false),
            () -> assertThat(orderRepository.countAll()).isEqualTo(1L),
            () -> assertThat(changedProduct.getStockQuantity()).isEqualTo(1),
            () -> assertThat(issuedCoupon.status()).isEqualTo(CouponStatus.USED)
        );
    }

    private List<Boolean> runConcurrently(int requestCount, Callable<Boolean> task) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    try {
                        return task.call();
                    } catch (CoreException e) {
                        return false;
                    }
                }));
            }
            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    private Long createTemplate(CouponType type, Long value, int maxIssuesPerUser) {
        return couponCommandService.createTemplate(new CouponCommand.CreateTemplate(
            "테스트 쿠폰",
            type,
            value,
            null,
            maxIssuesPerUser,
            ZonedDateTime.now().plusDays(1)
        )).couponId();
    }

    private Product saveProduct(String name, Long price, Integer stockQuantity) {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), name, "설명", price, stockQuantity));
    }
}
