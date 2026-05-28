package com.loopers.application.order;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.model.OrderItem;
import com.loopers.domain.order.repository.OrderItemRepository;
import com.loopers.domain.order.repository.OrderItemSnapshotRepository;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderApplicationServiceTest {

    private MemberService memberService;
    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private StockRepository stockRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderItemSnapshotRepository orderItemSnapshotRepository;
    private OrderApplicationService orderApplicationService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        productRepository = mock(ProductRepository.class);
        brandRepository = mock(BrandRepository.class);
        stockRepository = mock(StockRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        orderItemSnapshotRepository = mock(OrderItemSnapshotRepository.class);
        orderApplicationService = new OrderApplicationService(
            memberService, productRepository, brandRepository,
            stockRepository, orderRepository, orderItemRepository, orderItemSnapshotRepository
        );
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상 입력이면, 주문이 생성되고 orderId를 반환한다.")
        @Test
        void createsOrder_whenInputIsValid() {
            // Arrange
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product = mock(Product.class);
            when(product.getId()).thenReturn(1L);
            when(product.getBrandId()).thenReturn(10L);
            when(product.getName()).thenReturn("에어맥스");
            when(product.getPrice()).thenReturn(100_000L);

            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(10L);
            when(brand.getName()).thenReturn("나이키");

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of(brand));
            when(stockRepository.deductStock(anyLong(), anyInt())).thenReturn(1);

            Order savedOrder = mock(Order.class);
            when(savedOrder.getId()).thenReturn(100L);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> {
                List<OrderItem> items = inv.getArgument(0);
                // savedOrder.getId()로 생성된 OrderItem의 id는 0L(미저장 상태)
                return items;
            });
            when(orderItemSnapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Long orderId = orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 2)));

            // Assert
            assertThat(orderId).isEqualTo(100L);
            verify(orderRepository).save(any(Order.class));
            verify(orderItemRepository).saveAll(anyList());
            verify(orderItemSnapshotRepository).saveAll(anyList());
        }

        @DisplayName("존재하지 않는 회원이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberDoesNotExist() {
            // Arrange
            when(memberService.getMember("unknown"))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));

            // Act
            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("unknown", List.of(new OrderItemRequest(1L, 1)))
            );

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("중복된 productId가 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProductIds() {
            // Arrange
            Member member = mock(Member.class);
            when(memberService.getMember("user1")).thenReturn(member);

            // Act
            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(
                    new OrderItemRequest(1L, 1),
                    new OrderItemRequest(1L, 2)
                ))
            );

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품이 포함되어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductDoesNotExist() {
            // Arrange
            Member member = mock(Member.class);
            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L, 999L))).thenReturn(List.of(
                Product.create(10L, "에어맥스", "운동화", 100_000L)
            )); // 1개만 반환 → 수량 불일치

            // Act
            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(
                    new OrderItemRequest(1L, 1),
                    new OrderItemRequest(999L, 1)
                ))
            );

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 부족한 상품이 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // Arrange
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product1 = mock(Product.class);
            when(product1.getId()).thenReturn(1L);
            when(product1.getBrandId()).thenReturn(10L);
            when(product1.getName()).thenReturn("에어맥스");
            when(product1.getPrice()).thenReturn(100_000L);

            Product product2 = mock(Product.class);
            when(product2.getId()).thenReturn(2L);
            when(product2.getBrandId()).thenReturn(10L);
            when(product2.getName()).thenReturn("조던");
            when(product2.getPrice()).thenReturn(200_000L);

            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(10L);
            when(brand.getName()).thenReturn("나이키");

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(anyList())).thenReturn(List.of(product1, product2));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of(brand));
            when(stockRepository.deductStock(eq(1L), anyInt())).thenReturn(1);  // product1 성공
            when(stockRepository.deductStock(eq(2L), anyInt())).thenReturn(0);  // product2 재고 부족

            // Act
            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(
                    new OrderItemRequest(1L, 1),
                    new OrderItemRequest(2L, 1)
                ))
            );

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
