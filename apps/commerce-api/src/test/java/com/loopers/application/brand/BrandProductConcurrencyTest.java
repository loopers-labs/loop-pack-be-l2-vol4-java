package com.loopers.application.brand;

import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class BrandProductConcurrencyTest {

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드 삭제와 상품 추가가 동시에 요청되면, 삭제된 브랜드 하위에 활성 상품이 남지 않는다.")
    @Test
    void noActiveProductRemains_whenBrandDeletedAndProductCreatedConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                brandFacade.deleteBrand(brand.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {}
        });

        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                productFacade.createProduct(
                    new ProductCommand.Create(brand.getId(), "청바지", BigDecimal.valueOf(50000), 10L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {}
        });

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        BrandEntity result = brandJpaRepository.findById(brand.getId()).orElseThrow();
        long activeProductCount = productJpaRepository
            .findAllByBrandIdAndDeletedAtIsNull(brand.getId(), Pageable.unpaged())
            .getTotalElements();
        assertAll(
            () -> assertThat(result.getDeletedAt()).isNotNull(),
            () -> assertThat(activeProductCount).isZero()
        );
    }
}
