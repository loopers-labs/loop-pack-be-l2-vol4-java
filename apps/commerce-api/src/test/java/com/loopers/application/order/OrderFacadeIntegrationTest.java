package com.loopers.application.order;

import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderItemRepositoryImpl;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxRepositoryImpl;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @MockitoSpyBean
    private OrderItemRepositoryImpl orderItemRepositoryImpl;

    @MockitoSpyBean
    private OutboxRepositoryImpl outboxRepositoryImpl;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 정보를 주면, 재고가 차감되고 주문과 주문 상품이 생성된다.")
        @Test
        void createsOrderAndDeductsStock_whenValidInfoIsProvided() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 3)
            ));

            OrderInfo.Create result = orderFacade.createOrder(command);

            ProductStockEntity stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(result.orderId()).isNotNull(),
                () -> assertThat(stock.getQuantity()).isEqualTo(7L),
                () -> assertThat(orderItemJpaRepository.findAllByOrderId(result.orderId())).hasSize(1)
            );
        }

        @DisplayName("재고가 부족하면, 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsException_whenStockIsInsufficient() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 2L));

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 5)
            ));

            assertThrows(CoreException.class, () -> orderFacade.createOrder(command));
            assertThat(orderJpaRepository.count()).isZero();
        }

        @DisplayName("존재하지 않는 상품을 주문하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(9999L, 1)
            ));

            CoreException ex = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("주문 상품 저장에 실패하면, 재고 차감과 주문 생성이 모두 롤백된다.")
        @Test
        void rollsBackStockAndOrder_whenOrderItemSaveFails() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            doThrow(new RuntimeException("OrderItem 저장 실패")).when(orderItemRepositoryImpl).saveAll(anyList());

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 3)
            ));

            assertThrows(RuntimeException.class, () -> orderFacade.createOrder(command));

            ProductStockEntity stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getQuantity()).isEqualTo(10L),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("여러 상품 중 하나라도 존재하지 않으면, 모든 재고 차감이 롤백된다.")
        @Test
        void rollsBackAllStockDeductions_whenAnyProductDoesNotExist() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 3),
                new OrderCommand.Create.Item(9999L, 1)
            ));

            assertThrows(CoreException.class, () -> orderFacade.createOrder(command));

            ProductStockEntity stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getQuantity()).isEqualTo(10L),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("주문이 성공적으로 생성되면, ORDER.CREATED outbox 이벤트가 PENDING 상태로 저장된다.")
        @Test
        void savesOutboxEvent_whenOrderIsCreatedSuccessfully() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 2)
            ));

            orderFacade.createOrder(command);

            var outboxEvents = outboxJpaRepository.findAll();
            assertAll(
                () -> assertThat(outboxEvents).hasSize(1),
                () -> assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ORDER.CREATED"),
                () -> assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING)
            );
        }

        @DisplayName("outbox 이벤트 저장에 실패하면, 주문 생성과 재고 차감이 모두 롤백된다.")
        @Test
        void rollsBackOrderAndStock_whenOutboxEventSaveFails() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            doThrow(new RuntimeException("Outbox 저장 실패")).when(outboxRepositoryImpl).save(any());

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 3)
            ));

            assertThrows(RuntimeException.class, () -> orderFacade.createOrder(command));

            ProductStockEntity stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getQuantity()).isEqualTo(10L),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(outboxJpaRepository.count()).isZero()
            );
        }

        @DisplayName("주문 상품 저장에 실패하면, outbox 이벤트도 함께 롤백된다.")
        @Test
        void rollsBackOutboxEvent_whenOrderItemSaveFails() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));

            doThrow(new RuntimeException("OrderItem 저장 실패")).when(orderItemRepositoryImpl).saveAll(anyList());

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(product.getId(), 3)
            ));

            assertThrows(RuntimeException.class, () -> orderFacade.createOrder(command));
            assertThat(outboxJpaRepository.count()).isZero();
        }

        @DisplayName("여러 상품 중 하나라도 재고가 부족하면, 모든 재고 차감이 롤백된다.")
        @Test
        void rollsBackAllStockDeductions_whenAnyStockIsInsufficient() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity productA = productJpaRepository.save(
                new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
            ProductEntity productB = productJpaRepository.save(
                new ProductEntity(brand.getId(), "티셔츠", BigDecimal.valueOf(15000)));
            productStockJpaRepository.save(new ProductStockEntity(productA.getId(), 10L));
            productStockJpaRepository.save(new ProductStockEntity(productB.getId(), 2L));

            OrderCommand.Create command = new OrderCommand.Create(1L, null, List.of(
                new OrderCommand.Create.Item(productA.getId(), 3),
                new OrderCommand.Create.Item(productB.getId(), 5)
            ));

            assertThrows(CoreException.class, () -> orderFacade.createOrder(command));

            ProductStockEntity stockA = productStockJpaRepository.findByProductId(productA.getId()).orElseThrow();
            ProductStockEntity stockB = productStockJpaRepository.findByProductId(productB.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stockA.getQuantity()).isEqualTo(10L),
                () -> assertThat(stockB.getQuantity()).isEqualTo(2L),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }
    }
}
