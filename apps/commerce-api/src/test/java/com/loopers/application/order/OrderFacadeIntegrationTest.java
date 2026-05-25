package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserName;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long product1Id;
    private Long product2Id;

    @BeforeEach
    void setUp() {
        UserModel user = userRepository.save(new UserModel(
            new LoginId("loopers01"), "$2a$10$dummyEncodedHash",
            new UserName("홍길동"), LocalDate.of(2002, 5, 11), new Email("test@loopers.com")
        ));
        userId = user.getId();

        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel p1 = productRepository.save(new ProductModel(brand, "후드", "포근함", 50_000L));
        ProductModel p2 = productRepository.save(new ProductModel(brand, "맨투맨", "심플", 30_000L));
        product1Id = p1.getId();
        product2Id = p2.getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성 시")
    @Nested
    class PlaceOrder {

        @DisplayName("여러 상품을 주문하면 OrderItem 스냅샷이 저장되고 재고가 차감되며 총액이 계산된다")
        @Test
        void persistsOrderAndDecreasesStock() {
            // when
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                new OrderLineCommand(product1Id, 2),
                new OrderLineCommand(product2Id, 1)
            ));

            // then
            assertAll(
                () -> assertThat(info.status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(info.totalAmount()).isEqualTo(50_000L * 2 + 30_000L),
                () -> assertThat(info.items()).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(8),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("존재하지 않는 유저로 주문하면 NOT_FOUND 예외가 발생하고 재고는 변경되지 않는다")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(99_999L, List.of(new OrderLineCommand(product1Id, 1)))
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 상품을 포함하면 NOT_FOUND 예외가 발생하고 재고는 변경되지 않는다")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    new OrderLineCommand(product1Id, 1),
                    new OrderLineCommand(99_999L, 1)
                ))
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 모든 재고는 롤백된다 (단일 트랜잭션)")
        @Test
        void throwsConflict_andRollsBackEntireOrder_whenStockIsInsufficient() {
            // when - product1은 충분하지만 product2가 부족 (5개 < 10개)
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    new OrderLineCommand(product1Id, 1),
                    new OrderLineCommand(product2Id, 10)
                ))
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }
    }
}
