package com.loopers.application.product;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 수정(name/price 전체 행 UPDATE)이 동시 좋아요의 원자적 like_count 증감을 덮어쓰지 않는지(lost update) 검증.
 * Product 에 @DynamicUpdate 가 있어 like_count 가 dirty 가 아니면 UPDATE 에서 빠지므로 카운터가 보존된다.
 */
@DisplayName("상품 수정 ↔ 좋아요 동시성 (like_count lost update 방지)")
@SpringBootTest
class ProductModifyLikeConcurrencyIntegrationTest {

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(Product.create(brandId, "상품1", Money.of(1_000L))).getId();
        inventoryJpaRepository.save(Inventory.create(productId, 1_000));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 100건과 상품 수정 20건이 동시에 들어와도, like_count 는 정확히 100 이고 수정에 덮어써지지 않는다.")
    @Test
    void concurrentModifyAndLikes_doNotLoseLikeCount() throws InterruptedException {
        int likeUsers = 100;
        int modifyCount = 20;
        int total = likeUsers + modifyCount;

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(total);

        for (int i = 0; i < likeUsers; i++) {
            final long userId = 1_000L + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeApplicationService.register(userId, productId);
                } catch (Exception ignored) {
                    // 결함이 있으면 카운터 불일치로 드러난다
                } finally {
                    doneGate.countDown();
                }
            });
        }
        for (int i = 0; i < modifyCount; i++) {
            final int seq = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    productApplicationService.modify(
                            new ProductCriteria.Modify(productId, "수정상품" + seq, 2_000L, 500));
                } catch (Exception ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 작업이 30초 내에 끝나야 한다").isTrue();

        long rows = likeRepository.countByProductId(productId);
        long counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();

        assertThat(rows).as("실제 좋아요 행 수").isEqualTo(likeUsers);
        assertThat(counter)
                .as("상품 수정이 동시에 일어나도 like_count 는 좋아요 행 수와 정확히 일치해야 한다")
                .isEqualTo(likeUsers);
    }
}
