package com.loopers.order;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.infrastructure.InventoryJpaRepository;
import com.loopers.member.domain.Member;
import com.loopers.member.infrastructure.MemberJpaRepository;
import com.loopers.order.application.OrderFacade;
import com.loopers.order.domain.OrderLine;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.ConcurrencyTestSupport;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoryOrderConcurrencyTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private InventoryJpaRepository inventoryJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 상품에 여러 주문이 동시에 요청되어도 재고가 정확히 차감되고 초과 판매가 발생하지 않는다.")
    @Test
    void concurrentOrders_deductStockExactly_noOversell() throws InterruptedException {
        Member member = memberJpaRepository.save(new Member("user0001", "pw123456"));
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L));

        int stock = 5;
        int threads = 20;
        inventoryJpaRepository.save(new Inventory(product.getId(), stock));

        ConcurrencyTestSupport.Result result =
            ConcurrencyTestSupport.runConcurrently(
                threads,
                idx ->
                    orderFacade.createOrder(
                        member.getId(), List.of(new OrderLine(product.getId(), 1)), null));

        assertThat(result.success()).isEqualTo(stock);
        assertThat(result.failure()).isEqualTo(threads - stock);
        assertThat(inventoryJpaRepository.findByProductId(product.getId()).orElseThrow().getAvailableQuantity())
            .isZero();
        assertThat(orderJpaRepository.count()).isEqualTo(stock);
    }
}
