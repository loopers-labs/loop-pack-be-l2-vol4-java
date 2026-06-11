package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "삭제된 상품은 주문 대상에서 제외" 가 동시성 경쟁에서도 지켜지는지 검증.
 * 재고를 상품과 함께 소프트 삭제하고(주문과 같은 inventory 행 락 규약 공유),
 * 주문의 락 조회가 deleted_at IS NULL 로 필터하므로 삭제가 먼저 커밋되면 주문은 재고를 못 잠그고 거부된다.
 */
@DisplayName("주문 ↔ 상품 삭제 경쟁")
@SpringBootTest
class OrderDeleteRaceIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;
    private Long inventoryId;

    @BeforeEach
    void setUp() {
        brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(Product.create(brandId, "상품1", Money.of(1_000L))).getId();
        inventoryId = inventoryJpaRepository.save(Inventory.create(productId, 50)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품이 (소프트) 삭제된 뒤 들어온 주문은 재고를 잠그지 못하고 거부된다.")
    @Test
    void rejectsOrder_whenProductAlreadyDeleted() {
        productApplicationService.delete(productId);

        assertThatThrownBy(() -> orderApplicationService.place(new OrderCriteria.Place(
                1L, null, List.of(new OrderCriteria.Line(productId, 1)))))
                .isInstanceOf(CoreException.class);
    }

    @DisplayName("브랜드 일괄 삭제로 상품·재고가 삭제된 뒤 들어온 주문도 거부된다.")
    @Test
    void rejectsOrder_whenBrandDeleted() {
        brandApplicationService.delete(brandId);

        assertThatThrownBy(() -> orderApplicationService.place(new OrderCriteria.Place(
                1L, null, List.of(new OrderCriteria.Line(productId, 1)))))
                .isInstanceOf(CoreException.class);
    }

    @DisplayName("주문 다수와 삭제가 동시에 경쟁해도 데드락 없이 직렬화되며, 성공 주문 수 = 차감된 재고 수로 일관된다(유령 주문 없음).")
    @Test
    void concurrentOrdersAndDelete_serializeConsistently() throws InterruptedException {
        int stock = 50;
        int orderAttempts = 50;
        int total = orderAttempts + 1; // 주문들 + 삭제 1건

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(total);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < orderAttempts; i++) {
            final long userId = 1_000L + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderApplicationService.place(new OrderCriteria.Place(
                            userId, null, List.of(new OrderCriteria.Line(productId, 1))));
                    successCount.incrementAndGet();
                } catch (CoreException ignored) {
                    // 삭제가 먼저 이기면 재고 없음으로 거부 — 정상
                } catch (Exception ignored) {
                    // 락 경합 인프라 예외도 실패로 본다
                } finally {
                    doneGate.countDown();
                }
            });
        }
        executor.submit(() -> {
            try {
                startGate.await();
                productApplicationService.delete(productId);
            } catch (Exception ignored) {
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 작업이 30초 내에 끝나야 한다(데드락 없음)").isTrue();

        Inventory inventory = inventoryJpaRepository.findById(inventoryId).orElseThrow();
        int remaining = inventory.getQuantity();

        // 성공한 주문은 모두 삭제(재고 동결) '전에' 커밋됐으므로, 차감 합 == 성공 수.
        assertThat(successCount.get())
                .as("성공 주문 수 = 초기 재고 - 최종 재고 (유령 주문/초과 판매 없음)")
                .isEqualTo(stock - remaining);
        assertThat(successCount.get()).as("초과 판매는 없어야 한다").isLessThanOrEqualTo(stock);
        assertThat(inventory.isDeleted()).as("경쟁 종료 후 재고는 소프트 삭제 상태여야 한다").isTrue();
    }
}
