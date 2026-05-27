package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderFacade orderFacade;

    private ProductModel product(int price) {
        return ProductModel.builder()
            .brandId(1L)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(50)
            .build();
    }

    private BrandModel brand() {
        return BrandModel.builder()
            .rawName("감성 브랜드")
            .rawDescription("감성을 담은 브랜드")
            .build();
    }

    private OrderItemModel orderItem(Long productId, int unitPrice, int quantity) {
        return OrderItemModel.builder()
            .productId(productId)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(unitPrice)
            .rawQuantity(quantity)
            .build();
    }

    private OrderModel order(Long userId, int totalPrice) {
        return OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .totalPrice(totalPrice)
            .build();
    }

    @DisplayName("단건 주문을 생성할 때,")
    @Nested
    class CreateSingleOrder {

        private final Long userId = 1L;
        private final Long productId = 10L;

        @DisplayName("재고가 충분하면 재고를 차감하고 스냅샷을 기록한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenStockIsSufficient() {
            // arrange
            ProductModel product = product(39_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());
            given(productRepository.decreaseStock(product.getId(), 2)).willReturn(1);
            given(orderRepository.save(any(OrderModel.class), anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo orderInfo = orderFacade.createOrder(userId, itemCommands);

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(1),
                () -> assertThat(orderInfo.items().get(0).productName()).isEqualTo("감성 가디건"),
                () -> assertThat(orderInfo.items().get(0).brandName()).isEqualTo("감성 브랜드"),
                () -> assertThat(orderInfo.items().get(0).unitPrice()).isEqualTo(39_000),
                () -> assertThat(orderInfo.items().get(0).quantity()).isEqualTo(2),
                () -> assertThat(orderInfo.totalPrice()).isEqualTo(78_000),
                () -> then(productRepository).should().decreaseStock(product.getId(), 2),
                () -> then(orderRepository).should().save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("대상 상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파되고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(productRepository.getActiveById(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(productRepository).should(never()).decreaseStock(anyLong(), anyInt()),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("재고가 요청 수량에 미치지 못하면 CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_whenStockIsInsufficient() {
            // arrange
            ProductModel product = product(39_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 100));
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());
            given(productRepository.decreaseStock(product.getId(), 100)).willReturn(0);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("수량이 1 미만이면 BAD_REQUEST 예외가 발생하고 재고 차감·저장이 일어나지 않는다.")
        @Test
        void throwsBadRequest_whenQuantityIsLessThanOne() {
            // arrange
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 0));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(productRepository).should(never()).getActiveById(anyLong()),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }
    }

    @DisplayName("다중 항목 주문을 생성할 때,")
    @Nested
    class CreateMultiOrder {

        private final Long userId = 1L;
        private final Long firstProductId = 10L;
        private final Long secondProductId = 20L;

        @DisplayName("모든 항목의 재고가 충분하면 항목별 스냅샷을 기록하고 총액을 합산한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenAllItemsSucceed() {
            // arrange
            ProductModel firstProduct = product(10_000);
            ProductModel secondProduct = product(5_000);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 2));
            given(productRepository.getActiveById(firstProductId)).willReturn(firstProduct);
            given(productRepository.getActiveById(secondProductId)).willReturn(secondProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());
            given(productRepository.decreaseStock(firstProduct.getId(), 1)).willReturn(1);
            given(productRepository.decreaseStock(secondProduct.getId(), 2)).willReturn(1);
            given(orderRepository.save(any(OrderModel.class), anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo orderInfo = orderFacade.createOrder(userId, itemCommands);

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(2),
                () -> assertThat(orderInfo.totalPrice()).isEqualTo(20_000),
                () -> then(orderRepository).should().save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("같은 상품 식별자가 둘 이상 포함되면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsBadRequest_whenProductIsDuplicated() {
            // arrange
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(firstProductId, 2));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("어느 한 항목의 상품이 없으면 NOT_FOUND 예외가 전파되고 저장하지 않는다.")
        @Test
        void throwsNotFound_whenAnyItemProductIsAbsent() {
            // arrange
            ProductModel firstProduct = product(10_000);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 2));
            given(productRepository.getActiveById(firstProductId)).willReturn(firstProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());
            given(productRepository.decreaseStock(firstProduct.getId(), 1)).willReturn(1);
            given(productRepository.getActiveById(secondProductId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("어느 한 항목의 재고가 부족하면 CONFLICT 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsConflict_whenAnyItemStockIsInsufficient() {
            // arrange
            ProductModel firstProduct = product(10_000);
            ProductModel secondProduct = product(5_000);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 5));
            given(productRepository.getActiveById(firstProductId)).willReturn(firstProduct);
            given(productRepository.getActiveById(secondProductId)).willReturn(secondProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());
            given(productRepository.decreaseStock(firstProduct.getId(), 1)).willReturn(1);
            given(productRepository.decreaseStock(secondProduct.getId(), 5)).willReturn(0);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }
    }

    @DisplayName("본인 주문 상세를 조회할 때,")
    @Nested
    class ReadMyOrder {

        private final Long authUserId = 1L;
        private final Long orderId = 100L;

        @DisplayName("본인 주문이면 항목을 포함한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenOwnedByUser() {
            // arrange
            OrderModel order = order(authUserId, 78_000);
            given(orderRepository.getActiveByIdAndUserId(orderId, authUserId)).willReturn(order);
            given(orderRepository.findActiveItemsByOrderId(order.getId())).willReturn(List.of(orderItem(10L, 39_000, 2)));

            // act
            OrderInfo orderInfo = orderFacade.readMyOrder(authUserId, orderId);

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(1),
                () -> assertThat(orderInfo.totalPrice()).isEqualTo(78_000)
            );
        }

        @DisplayName("주문이 없거나 타인 소유면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAbsentOrNotOwned() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(orderId, authUserId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrder(authUserId, orderId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("본인 주문 내역을 날짜 범위로 조회할 때,")
    @Nested
    class ReadMyOrders {

        private final Long userId = 1L;

        @DisplayName("시작일·종료일이 없으면 오늘 기준 한 달 전부터 오늘까지의 범위로 조회한다.")
        @Test
        void appliesDefaultRange_whenDatesAreAbsent() {
            // arrange
            LocalDate today = LocalDate.now();
            ZonedDateTime expectedStart = today.minusMonths(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime expectedEndExclusive = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            given(orderRepository.findActiveByUserIdAndOrderedAtBetween(userId, expectedStart, expectedEndExclusive, 0, 20))
                .willReturn(Page.empty());

            // act
            orderFacade.readMyOrders(userId, null, null, 0, 20);

            // assert
            then(orderRepository).should().findActiveByUserIdAndOrderedAtBetween(userId, expectedStart, expectedEndExclusive, 0, 20);
        }

        @DisplayName("시작일이 종료일보다 늦으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStartIsAfterEnd() {
            // arrange
            LocalDate startAt = LocalDate.now();
            LocalDate endAt = startAt.minusDays(1);

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrders(userId, startAt, endAt, 0, 20))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시작일이 오늘 이후이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStartIsAfterToday() {
            // arrange
            LocalDate startAt = LocalDate.now().plusDays(1);
            LocalDate endAt = LocalDate.now().plusDays(2);

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrders(userId, startAt, endAt, 0, 20))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("관리자 주문 목록을 조회할 때,")
    @Nested
    class ReadOrders {

        @DisplayName("전체 주문을 헤더 레벨 정보 페이지로 변환해 반환한다.")
        @Test
        void returnsAdminSummaryInfoPage() {
            // arrange
            OrderModel order = order(7L, 78_000);
            given(orderRepository.findActiveByPage(0, 20)).willReturn(new PageImpl<>(List.of(order)));

            // act
            Page<OrderAdminSummaryInfo> result = orderFacade.readOrders(0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).userId()).isEqualTo(7L),
                () -> assertThat(result.getContent().get(0).totalPrice()).isEqualTo(78_000)
            );
        }
    }

    @DisplayName("관리자 주문 상세를 조회할 때,")
    @Nested
    class ReadOrder {

        private final Long orderId = 100L;

        @DisplayName("존재하는 주문이면 회원 식별자와 항목을 포함한 상세를 반환한다.")
        @Test
        void returnsAdminInfo_whenOrderExists() {
            // arrange
            OrderModel order = order(7L, 78_000);
            given(orderRepository.getActiveById(orderId)).willReturn(order);
            given(orderRepository.findActiveItemsByOrderId(order.getId())).willReturn(List.of(orderItem(10L, 39_000, 2)));

            // act
            OrderAdminInfo orderAdminInfo = orderFacade.readOrder(orderId);

            // assert
            assertAll(
                () -> assertThat(orderAdminInfo.userId()).isEqualTo(7L),
                () -> assertThat(orderAdminInfo.items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenOrderIsAbsent() {
            // arrange
            given(orderRepository.getActiveById(orderId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.readOrder(orderId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
