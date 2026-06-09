package com.loopers.order.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.BrandModel;
import com.loopers.member.application.MemberService;
import com.loopers.member.domain.MemberModel;
import com.loopers.order.domain.OrderLine;
import com.loopers.order.domain.OrderModel;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fake.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderFacadeTest {

    private FakeProductRepository productRepository;
    private FakeOrderRepository orderRepository;
    private OrderFacade orderFacade;
    private Long memberId;
    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() {
        FakeMemberRepository memberRepository = new FakeMemberRepository();
        productRepository = new FakeProductRepository();
        FakeBrandRepository brandRepository = new FakeBrandRepository();
        orderRepository = new FakeOrderRepository();

        MemberService memberService = new MemberService(memberRepository);
        ProductService productService = new ProductService(productRepository);
        BrandService brandService = new BrandService(brandRepository);
        OrderService orderService = new OrderService(orderRepository);

        orderFacade =
            new OrderFacade(memberService, productService, brandService, orderService);

        MemberModel member = memberRepository.save(new MemberModel("member01", "pw123456"));
        BrandModel brand = brandRepository.save(new BrandModel("브랜드", "설명"));
        ProductModel p1 = productRepository.save(new ProductModel(brand.getId(), "A", "설명", 1_000L, 10));
        ProductModel p2 = productRepository.save(new ProductModel(brand.getId(), "B", "설명", 2_000L, 5));
        memberId = member.getId();
        productId1 = p1.getId();
        productId2 = p2.getId();
    }

    @DisplayName("정상 주문 흐름에서,")
    @Nested
    class Success {
        @DisplayName("여러 상품을 주문하면 재고가 차감되고 총액이 계산된다.")
        @Test
        void createsOrder_andDeductsStock() {
            OrderInfo info =
                orderFacade.createOrder(
                    memberId, List.of(new OrderLine(productId1, 2), new OrderLine(productId2, 1)));

            assertThat(info.totalAmount()).isEqualTo(2 * 1_000L + 2_000L);
            assertThat(info.items()).hasSize(2);
            assertThat(productRepository.find(productId1).orElseThrow().getStock()).isEqualTo(8);
            assertThat(productRepository.find(productId2).orElseThrow().getStock()).isEqualTo(4);
        }

        @DisplayName("주문 후 본인 주문 상세를 조회할 수 있다.")
        @Test
        void canReadOwnOrder() {
            OrderInfo created =
                orderFacade.createOrder(memberId, List.of(new OrderLine(productId1, 1)));
            OrderInfo found = orderFacade.getMyOrder(memberId, created.id());
            assertThat(found.id()).isEqualTo(created.id());
        }
    }

    @DisplayName("예외 주문 흐름에서,")
    @Nested
    class Failure {
        @DisplayName("존재하지 않는 회원이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberMissing() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> orderFacade.createOrder(999L, List.of(new OrderLine(productId1, 1))));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> orderFacade.createOrder(memberId, List.of(new OrderLine(999L, 1))));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsConflict_whenStockInsufficient() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> orderFacade.createOrder(memberId, List.of(new OrderLine(productId2, 99))));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(orderFacade.getMyOrders(memberId, null, null)).isEmpty();
        }

        @DisplayName("타인의 주문은 조회할 수 없어 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenReadingOthersOrder() {
            OrderInfo created =
                orderFacade.createOrder(memberId, List.of(new OrderLine(productId1, 1)));
            CoreException result =
                assertThrows(
                    CoreException.class, () -> orderFacade.getMyOrder(999L, created.id()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 목록 날짜 필터에서,")
    @Nested
    class DateFilter {

        private void seedOrder(long id, String date) {
            OrderModel order = OrderModel.create(memberId);
            IdFixtures.assignId(order, id);
            ReflectionTestUtils.setField(
                order,
                "createdAt",
                ZonedDateTime.of(LocalDate.parse(date).atStartOfDay(), ZoneId.of("Asia/Seoul")));
            orderRepository.save(order);
        }

        @DisplayName("startAt/endAt 범위 내 주문만 반환한다.")
        @Test
        void filtersByDateRange() {
            seedOrder(1L, "2026-02-01");
            seedOrder(2L, "2026-02-05");
            seedOrder(3L, "2026-02-20");

            List<OrderInfo> result =
                orderFacade.getMyOrders(
                    memberId, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-10"));

            assertThat(result).extracting(OrderInfo::id).containsExactlyInAnyOrder(1L, 2L);
        }

        @DisplayName("날짜 파라미터가 없으면 전체 주문을 반환한다.")
        @Test
        void returnsAll_whenNoDateGiven() {
            seedOrder(1L, "2026-02-01");
            seedOrder(2L, "2026-02-20");

            List<OrderInfo> result = orderFacade.getMyOrders(memberId, null, null);

            assertThat(result).hasSize(2);
        }
    }
}
