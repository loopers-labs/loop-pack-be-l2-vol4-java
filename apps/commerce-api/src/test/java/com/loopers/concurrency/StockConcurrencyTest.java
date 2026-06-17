package com.loopers.concurrency;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private List<Member> members;
    private Product product;
    private static final int STOCK_QUANTITY = 10;
    private static final int CONCURRENT_ORDERS = 20;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("나이키"));
        product = productRepository.save(Product.create(brand.getId(), "에어맥스", "운동화", 100_000L));
        stockRepository.save(Stock.create(product.getId(), STOCK_QUANTITY));

        members = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_ORDERS; i++) {
            members.add(memberService.register(
                "stockUser" + i, "Password1!", "유저" + i, "1990-01-01", "stock" + i + "@test.com"
            ));
        }
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고가 10개인 상품에 20명이 동시에 1개씩 주문해도, 재고가 0개 이하로 떨어지지 않는다.")
    @Test
    void stock_neverGoesBelowZero_underConcurrentOrders() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_ORDERS);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ORDERS);

        for (int i = 0; i < CONCURRENT_ORDERS; i++) {
            final String loginId = members.get(i).getLoginId();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderApplicationService.createOrder(
                        loginId,
                        List.of(new OrderItemRequest(product.getId(), 1)),
                        null
                    );
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(STOCK_QUANTITY);

        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(0);
    }
}
