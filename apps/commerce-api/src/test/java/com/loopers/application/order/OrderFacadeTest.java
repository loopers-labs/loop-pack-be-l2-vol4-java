package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private Long shoesId;  // 단가 1000, 재고 10
    private Long sockId;   // 단가 2000, 재고 3

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        shoesId = productRepository.save(
                new ProductModel(brand.getId(), "운동화", null, Money.of(1000L), Quantity.of(10), null)).getId();
        sockId = productRepository.save(
                new ProductModel(brand.getId(), "양말", null, Money.of(2000L), Quantity.of(3), null)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderCriteria.Line line(Long productId, int qty) {
        return new OrderCriteria.Line(productId, qty);
    }

    @DisplayName("여러 상품을 주문하면 총액은 소계 합이고, 각 상품 재고가 차감되며, 상태는 PENDING 이다.")
    @Test
    void placeOrder_calculatesTotalAndDecreasesStock() {
        // 1000×2 + 2000×1 = 4000
        OrderInfo info = orderFacade.placeOrder(
                new OrderCriteria(USER_ID, List.of(line(shoesId, 2), line(sockId, 1))));

        assertThat(info.totalAmount()).isEqualTo(4000L);
        assertThat(info.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(info.items()).hasSize(2);
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(8));
        assertThat(productRepository.findById(sockId).get().getStockQuantity()).isEqualTo(Quantity.of(2));
    }

    @DisplayName("한 상품이라도 재고가 부족하면 BAD_REQUEST 이고, 앞서 차감된 다른 상품 재고도 전부 롤백된다. (All-or-Nothing)")
    @Test
    void placeOrder_rollsBackAll_whenAnyStockInsufficient() {
        // 운동화 2개(가능) + 양말 5개(재고 3 → 부족) → 전체 실패
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, List.of(line(shoesId, 2), line(sockId, 5)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        // 운동화는 먼저 차감됐지만 롤백되어 원래 재고 10 그대로
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(10));
        assertThat(productRepository.findById(sockId).get().getStockQuantity()).isEqualTo(Quantity.of(3));
    }

    @DisplayName("존재하지 않는 상품을 주문하면 NOT_FOUND.")
    @Test
    void placeOrder_throwsNotFound_whenProductDoesNotExist() {
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, List.of(line(999_999L, 1)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("주문 항목이 비어있으면 BAD_REQUEST.")
    @Test
    void placeOrder_throwsBadRequest_whenItemsEmpty() {
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(new OrderCriteria(USER_ID, List.of())));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
