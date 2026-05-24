package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderFacadeIntegrationTest {

    private final OrderFacade orderFacade;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderFacadeIntegrationTest(
        OrderFacade orderFacade,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때 ")
    @Nested
    class CreateOrder {

        @DisplayName("주문 가능한 상품과 수량이 주어지면, 재고를 차감하고 주문 스냅샷을 저장한다.")
        @Test
        void createsOrderAndDeductsStock_whenProductsAreOrderable() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            Product iphoneMax = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productStockService.createProductStock(iphoneMax.getId(), 5);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2),
                new CreateOrderCommand.Item(iphoneMax.getId(), 1)
            ));

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(userId),
                () -> assertThat(result.orderTotalPrice()).isEqualTo(5_000_000L),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::productName)
                    .containsExactly("아이폰 16 Pro", "아이폰 16 Pro Max"),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::brandName)
                    .containsExactly("애플", "애플"),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::totalPrice)
                    .containsExactly(3_100_000L, 1_900_000L),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(8),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("하나의 상품이라도 재고가 부족하면, 주문 생성을 실패하고 차감된 재고도 롤백한다.")
        @Test
        void throwsConflictAndRollsBackStock_whenAnyProductStockIsInsufficient() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            Product iphoneMax = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productStockService.createProductStock(iphoneMax.getId(), 5);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2),
                new CreateOrderCommand.Item(iphoneMax.getId(), 6)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("삭제된 상품이 포함되면, 주문 생성을 실패하고 재고를 차감하지 않는다.")
        @Test
        void throwsNotFoundAndKeepsStock_whenProductIsDeleted() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productService.deleteProduct(iphone.getId());
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("삭제된 브랜드의 상품이 포함되면, 주문 생성을 실패하고 재고를 차감하지 않는다.")
        @Test
        void throwsNotFoundAndKeepsStock_whenProductBrandIsDeleted() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            brandService.deleteBrand(brand.getId());
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 상품이 포함되면, NOT_FOUND 예외를 던진다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long userId = 1L;
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(999L, 1)
            ));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(orderJpaRepository.count()).isZero();
        }

        @DisplayName("같은 상품 ID가 중복되면, BAD_REQUEST 예외를 던지고 재고를 차감하지 않는다.")
        @Test
        void throwsBadRequestAndKeepsStock_whenProductIdIsDuplicated() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1),
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // arrange
            Long userId = 1L;
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of());

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(orderJpaRepository.count()).isZero();
        }

        @DisplayName("주문 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            Long userId = 1L;
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(999L, 0)
            ));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(orderJpaRepository.count()).isZero();
        }
    }
}
