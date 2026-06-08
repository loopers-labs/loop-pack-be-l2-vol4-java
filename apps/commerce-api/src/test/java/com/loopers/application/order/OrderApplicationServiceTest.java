package com.loopers.application.order;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.model.OrderItem;
import com.loopers.domain.order.model.OrderItemSnapshot;
import com.loopers.domain.order.model.OrderItemStatus;
import com.loopers.domain.order.repository.OrderItemRepository;
import com.loopers.domain.order.repository.OrderItemSnapshotRepository;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
    private IssuedCouponRepository issuedCouponRepository;
    private CouponTemplateRepository couponTemplateRepository;
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
        issuedCouponRepository = mock(IssuedCouponRepository.class);
        couponTemplateRepository = mock(CouponTemplateRepository.class);
        orderApplicationService = new OrderApplicationService(
            memberService, productRepository, brandRepository,
            stockRepository, orderRepository, orderItemRepository, orderItemSnapshotRepository,
            issuedCouponRepository, couponTemplateRepository
        );
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("쿠폰 없이 정상 입력이면, 주문이 생성되고 orderId를 반환한다.")
        @Test
        void createsOrder_withoutCoupon() {
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
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemSnapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            Long orderId = orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 2)), null);

            assertThat(orderId).isEqualTo(100L);
            verify(orderRepository).save(any(Order.class));
        }

        @DisplayName("유효한 쿠폰을 적용하면, 할인이 반영된 주문이 생성된다.")
        @Test
        void createsOrder_withValidCoupon() {
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

            IssuedCoupon issuedCoupon = mock(IssuedCoupon.class);
            when(issuedCoupon.getMemberId()).thenReturn(1L);
            when(issuedCoupon.getCouponTemplateId()).thenReturn(5L);

            CouponTemplate template = CouponTemplate.create("1만원 할인", CouponType.FIXED, 10_000L, null,
                ZonedDateTime.now().plusDays(30));

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of(brand));
            when(stockRepository.deductStock(anyLong(), anyInt())).thenReturn(1);
            when(issuedCouponRepository.findByIdWithLock(42L)).thenReturn(Optional.of(issuedCoupon));
            when(couponTemplateRepository.findById(5L)).thenReturn(Optional.of(template));

            Order savedOrder = mock(Order.class);
            when(savedOrder.getId()).thenReturn(100L);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemSnapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            Long orderId = orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 1)), 42L);

            assertThat(orderId).isEqualTo(100L);
            verify(issuedCoupon).use();
        }

        @DisplayName("존재하지 않는 쿠폰 ID로 주문 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product = mock(Product.class);
            when(product.getId()).thenReturn(1L);
            when(product.getBrandId()).thenReturn(10L);
            when(product.getPrice()).thenReturn(100_000L);

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of());
            when(stockRepository.deductStock(anyLong(), anyInt())).thenReturn(1);
            when(issuedCouponRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 1)), 999L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인 소유 쿠폰으로 주문 시, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenCouponBelongsToOtherMember() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product = mock(Product.class);
            when(product.getId()).thenReturn(1L);
            when(product.getBrandId()).thenReturn(10L);
            when(product.getPrice()).thenReturn(100_000L);

            IssuedCoupon issuedCoupon = mock(IssuedCoupon.class);
            when(issuedCoupon.getMemberId()).thenReturn(99L); // 다른 멤버

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of());
            when(stockRepository.deductStock(anyLong(), anyInt())).thenReturn(1);
            when(issuedCouponRepository.findByIdWithLock(42L)).thenReturn(Optional.of(issuedCoupon));

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 1)), 42L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문 시, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product = mock(Product.class);
            when(product.getId()).thenReturn(1L);
            when(product.getBrandId()).thenReturn(10L);
            when(product.getPrice()).thenReturn(100_000L);

            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 5L);
            issuedCoupon.use();

            CouponTemplate template = CouponTemplate.create("1만원 할인", CouponType.FIXED, 10_000L, null,
                ZonedDateTime.now().plusDays(30));

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of());
            when(stockRepository.deductStock(anyLong(), anyInt())).thenReturn(1);
            when(issuedCouponRepository.findByIdWithLock(42L)).thenReturn(Optional.of(issuedCoupon));
            when(couponTemplateRepository.findById(5L)).thenReturn(Optional.of(template));

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(new OrderItemRequest(1L, 1)), 42L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 회원이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberDoesNotExist() {
            when(memberService.getMember("unknown"))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("unknown", List.of(new OrderItemRequest(1L, 1)), null)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("중복된 productId가 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProductIds() {
            Member member = mock(Member.class);
            when(memberService.getMember("user1")).thenReturn(member);

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(
                    new OrderItemRequest(1L, 1),
                    new OrderItemRequest(1L, 2)
                ), null)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 부족한 상품이 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);

            Product product1 = mock(Product.class);
            when(product1.getId()).thenReturn(1L);
            when(product1.getBrandId()).thenReturn(10L);
            when(product1.getPrice()).thenReturn(100_000L);

            Product product2 = mock(Product.class);
            when(product2.getId()).thenReturn(2L);
            when(product2.getBrandId()).thenReturn(10L);
            when(product2.getPrice()).thenReturn(200_000L);

            when(memberService.getMember("user1")).thenReturn(member);
            when(productRepository.findAllByIdIn(anyList())).thenReturn(List.of(product1, product2));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of());
            when(stockRepository.deductStock(eq(1L), anyInt())).thenReturn(1);
            when(stockRepository.deductStock(eq(2L), anyInt())).thenReturn(0);

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.createOrder("user1", List.of(
                    new OrderItemRequest(1L, 1),
                    new OrderItemRequest(2L, 1)
                ), null)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 목록을 조회할 때, ")
    @Nested
    class GetOrders {

        @DisplayName("정상 요청이면, 해당 회원의 주문 목록(요약)을 반환한다.")
        @Test
        void returnsOrderSummaries_whenMemberExists() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(10L);
            when(order.getOriginalAmount()).thenReturn(100_000L);
            when(order.getDiscountAmount()).thenReturn(0L);
            when(order.getTotalAmount()).thenReturn(100_000L);
            when(order.getCreatedAt()).thenReturn(null);

            Page<Order> orderPage = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
            when(orderRepository.findAllByMemberId(eq(1L), any(), any(), any())).thenReturn(orderPage);

            Page<OrderSummary> result = orderApplicationService.getOrders("user1", null, null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).orderId()).isEqualTo(10L);
            assertThat(result.getContent().get(0).totalAmount()).isEqualTo(100_000L);
        }
    }

    @DisplayName("주문 상세를 조회할 때, ")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문이면, 주문 상세(items 포함)를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderBelongsToMember() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(10L);
            when(order.getMemberId()).thenReturn(1L);
            when(order.getOriginalAmount()).thenReturn(100_000L);
            when(order.getDiscountAmount()).thenReturn(0L);
            when(order.getTotalAmount()).thenReturn(100_000L);
            when(order.getCreatedAt()).thenReturn(null);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

            OrderItem item = mock(OrderItem.class);
            when(item.getId()).thenReturn(100L);
            when(item.getQuantity()).thenReturn(2);
            when(item.getStatus()).thenReturn(OrderItemStatus.ORDERED);
            when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));

            OrderItemSnapshot snapshot = mock(OrderItemSnapshot.class);
            when(snapshot.getOrderItemId()).thenReturn(100L);
            when(snapshot.getProductName()).thenReturn("에어맥스");
            when(snapshot.getBrandName()).thenReturn("나이키");
            when(snapshot.getPrice()).thenReturn(50_000L);
            when(orderItemSnapshotRepository.findAllByOrderItemIdIn(List.of(100L))).thenReturn(List.of(snapshot));

            OrderDetail result = orderApplicationService.getOrder("user1", 10L);

            assertThat(result.orderId()).isEqualTo(10L);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productName()).isEqualTo("에어맥스");
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.getOrder("user1", 999L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인의 주문이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToOtherMember() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(memberService.getMember("user1")).thenReturn(member);

            Order order = mock(Order.class);
            when(order.getMemberId()).thenReturn(99L);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

            CoreException ex = assertThrows(CoreException.class, () ->
                orderApplicationService.getOrder("user1", 10L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
