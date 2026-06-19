package com.loopers.domain.concurrency;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.product.ProductLikeCountFlushService;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.like.ProductLikeJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class TransactionalConcurrencyIntegrationTest {

    private final OrderFacade orderFacade;
    private final CouponFacade couponFacade;
    private final ProductLikeFacade productLikeFacade;
    private final ProductLikeCountFlushService productLikeCountFlushService;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final ProductLikeJpaRepository productLikeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    TransactionalConcurrencyIntegrationTest(
        OrderFacade orderFacade,
        CouponFacade couponFacade,
        ProductLikeFacade productLikeFacade,
        ProductLikeCountFlushService productLikeCountFlushService,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        ProductLikeJpaRepository productLikeJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.couponFacade = couponFacade;
        this.productLikeFacade = productLikeFacade;
        this.productLikeCountFlushService = productLikeCountFlushService;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.productLikeJpaRepository = productLikeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("동일 상품을 동시에 주문해도 재고만큼만 주문되고 재고가 음수가 되지 않는다.")
    @Test
    void deductsStockCorrectly_whenSameProductIsOrderedConcurrently() throws InterruptedException {
        // arrange
        int stock = 5;
        int threadCount = 10;
        Long brandId = createBrand();
        ProductJpaEntity product = createProduct(brandId, "동시성 니트", stock);
        AtomicInteger successCount = new AtomicInteger();

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            orderFacade.createOrder(
                "user" + index,
                List.of(new OrderProductCommand(product.getId(), 1))
            );
            successCount.incrementAndGet();
        });

        // assert
        ProductJpaEntity updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

        assertAll(
            () -> assertThat(successCount).hasValue(stock),
            () -> assertConflictFailures(failures, threadCount - stock),
            () -> assertThat(updatedProduct.getStock()).isZero(),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(stock)
        );
    }

    @DisplayName("동일 쿠폰으로 동시에 주문해도 쿠폰은 한 번만 사용된다.")
    @Test
    void usesCouponOnlyOnce_whenSameCouponIsUsedConcurrently() throws InterruptedException {
        // arrange
        int threadCount = 5;
        String userLoginId = "user1234";
        ZonedDateTime now = ZonedDateTime.now();
        Long brandId = createBrand();
        List<ProductJpaEntity> products = IntStream.range(0, threadCount)
            .mapToObj(index -> createProduct(brandId, "쿠폰 상품 " + index, 1))
            .toList();
        CouponInfo.Template coupon = couponFacade.createCoupon(
            "동시성 쿠폰",
            CouponType.FIXED,
            1_000L,
            0L,
            now.plusDays(1)
        );
        couponFacade.issueCoupon(coupon.id(), userLoginId, now);
        AtomicInteger successCount = new AtomicInteger();

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            orderFacade.createOrder(
                userLoginId,
                List.of(new OrderProductCommand(products.get(index).getId(), 1)),
                coupon.id()
            );
            successCount.incrementAndGet();
        });

        // assert
        IssuedCoupon issuedCoupon = issuedCouponJpaRepository
            .findByCouponIdAndUserLoginIdAndDeletedAtIsNull(coupon.id(), userLoginId)
            .map(IssuedCouponJpaEntity::toDomain)
            .orElseThrow();
        Integer remainingStock = productJpaRepository.findAllById(
                products.stream()
                    .map(ProductJpaEntity::getId)
                    .toList()
            ).stream()
            .mapToInt(ProductJpaEntity::getStock)
            .sum();

        assertAll(
            () -> assertThat(successCount).hasValue(1),
            () -> assertConflictFailures(failures, threadCount - 1),
            () -> assertThat(issuedCoupon.getStatus()).isEqualTo(CouponStatus.USED),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
            () -> assertThat(remainingStock).isEqualTo(threadCount - 1)
        );
    }

    @DisplayName("동일 쿠폰을 같은 회원에게 동시에 발급해도 한 번만 발급된다.")
    @Test
    void issuesCouponOnlyOnce_whenSameCouponIsIssuedConcurrently() throws InterruptedException {
        // arrange
        int threadCount = 5;
        String userLoginId = "user1234";
        ZonedDateTime now = ZonedDateTime.now();
        CouponInfo.Template coupon = couponFacade.createCoupon(
            "동시 발급 쿠폰",
            CouponType.RATE,
            10L,
            0L,
            now.plusDays(1)
        );
        AtomicInteger successCount = new AtomicInteger();

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            couponFacade.issueCoupon(coupon.id(), userLoginId, now);
            successCount.incrementAndGet();
        });

        // assert
        long issuedCouponCount = issuedCouponJpaRepository
            .findAllByUserLoginIdAndDeletedAtIsNull(userLoginId)
            .stream()
            .filter(issuedCoupon -> issuedCoupon.getCouponId().equals(coupon.id()))
            .count();

        assertAll(
            () -> assertThat(successCount).hasValue(1),
            () -> assertConflictFailures(failures, threadCount - 1),
            () -> assertThat(issuedCouponCount).isEqualTo(1)
        );
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요를 눌러도 좋아요 수가 정상 반영된다.")
    @Test
    void increasesLikeCountCorrectly_whenSameProductIsLikedConcurrently() throws InterruptedException {
        // arrange
        int threadCount = 10;
        Long brandId = createBrand();
        ProductJpaEntity product = createProduct(brandId, "좋아요 니트", 10);

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            productLikeFacade.likeProduct("user" + index, product.getId());
        });
        productLikeCountFlushService.flushDirtyLikeCounts();

        // assert
        ProductJpaEntity updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

        assertAll(
            () -> assertThat(failures).isEmpty(),
            () -> assertThat(updatedProduct.getLikeCount()).isEqualTo(threadCount),
            () -> assertThat(productLikeJpaRepository.count()).isEqualTo(threadCount)
        );
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요를 취소해도 좋아요 수가 정상 반영된다.")
    @Test
    void decreasesLikeCountCorrectly_whenSameProductIsUnlikedConcurrently() throws InterruptedException {
        // arrange
        int threadCount = 10;
        Long brandId = createBrand();
        ProductJpaEntity product = createProduct(brandId, "좋아요 취소 니트", 10);
        for (int index = 0; index < threadCount; index++) {
            productLikeFacade.likeProduct("user" + index, product.getId());
        }

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            productLikeFacade.unlikeProduct("user" + index, product.getId());
        });
        productLikeCountFlushService.flushDirtyLikeCounts();

        // assert
        ProductJpaEntity updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

        assertAll(
            () -> assertThat(failures).isEmpty(),
            () -> assertThat(updatedProduct.getLikeCount()).isZero(),
            () -> assertThat(productLikeJpaRepository.count()).isZero()
        );
    }

    private Long createBrand() {
        return brandJpaRepository.save(BrandJpaEntity.from(new Brand("테스트 브랜드", "테스트 브랜드 설명"))).getId();
    }

    private ProductJpaEntity createProduct(Long brandId, String name, Integer stock) {
        return productJpaRepository.save(ProductJpaEntity.from(
            new Product(brandId, name, "테스트 상품 설명", 10_000L, stock)
        ));
    }

    private List<Throwable> runConcurrently(int threadCount, ConcurrentTask task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int index = 0; index < threadCount; index++) {
                final int taskIndex = index;
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        task.run(taskIndex);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();

            return failures;
        } finally {
            executorService.shutdownNow();
        }
    }

    private void assertConflictFailures(List<Throwable> failures, int expectedCount) {
        assertThat(failures).hasSize(expectedCount);
        assertThat(failures).allSatisfy(throwable -> {
            assertThat(throwable).isInstanceOf(CoreException.class);
            assertThat(((CoreException) throwable).getErrorType()).isEqualTo(ErrorType.CONFLICT);
        });
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        void run(int index) throws Exception;
    }
}
