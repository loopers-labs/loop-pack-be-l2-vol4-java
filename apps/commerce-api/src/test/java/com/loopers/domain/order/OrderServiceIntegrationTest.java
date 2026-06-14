package com.loopers.domain.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.domain.quantity.Quantity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

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

    @DisplayName("주문을 접수할 때, ")
    @Nested
    class Place {
        @DisplayName("Order와 OrderItem들이 저장되고, 재고가 차감되며, OrderItem들은 저장된 Order의 id로 매핑된다.")
        @Test
        void persistsOrderAndItemsAndDecreasesStock() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
            Product airmax = productJpaRepository.save(new Product("에어맥스", null,
                new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
            Product socks = productJpaRepository.save(new Product("양말", null,
                new Money(BigDecimal.valueOf(500)), new Stock(10), brand.getId()));
            Long userId = 1L;
            List<OrderLine> lines = List.of(
                new OrderLine(airmax.getId(), new Quantity(2)),
                new OrderLine(socks.getId(), new Quantity(1))
            );

            // act
            List<OrderItem> items = orderService.prepareItems(lines);
            Order saved = orderService.complete(userId, items, Money.ZERO);

            // assert
            List<OrderItem> persistedItems = orderItemJpaRepository.findAll();
            Product reloadedAirmax = productJpaRepository.findById(airmax.getId()).orElseThrow();
            Product reloadedSocks = productJpaRepository.findById(socks.getId()).orElseThrow();
            assertAll(
                () -> assertThat(orderJpaRepository.findById(saved.getId())).isPresent(),
                () -> assertThat(persistedItems).hasSize(2),
                () -> assertThat(persistedItems).extracting(OrderItem::getOrderId)
                    .containsOnly(saved.getId()),
                () -> assertThat(saved.getTotalAmount().getAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(2500)),
                () -> assertThat(reloadedAirmax.getStock().getQuantity()).isEqualTo(8),
                () -> assertThat(reloadedSocks.getStock().getQuantity()).isEqualTo(9)
            );
        }
    }
}
