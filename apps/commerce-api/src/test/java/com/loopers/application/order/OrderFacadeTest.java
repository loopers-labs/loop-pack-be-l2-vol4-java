package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderFacadeTest {

    private final OrderService orderService = mock(OrderService.class);
    private final UserService userService = mock(UserService.class);
    private final OrderFacade orderFacade = new OrderFacade(orderService, userService);

    @DisplayName("주문을 생성하면, 로그인 ID로 유저를 식별해 주문 서비스에 위임하고 결과를 반환한다.")
    @Test
    void createsOrder() {
        // arrange
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(7L);
        when(userService.getUser("tester")).thenReturn(user);
        List<OrderLine> lines = List.of(new OrderLine(11L, 2));
        OrderModel order = new OrderModel(7L,
            List.of(new OrderItemModel(11L, "에어맥스", Money.of(1000L), Quantity.of(2))));
        when(orderService.createOrder(7L, lines)).thenReturn(order);

        // act
        OrderInfo info = orderFacade.createOrder("tester", lines);

        // assert
        verify(orderService).createOrder(7L, lines);
        assertAll(
            () -> assertThat(info.userId()).isEqualTo(7L),
            () -> assertThat(info.status()).isEqualTo("PENDING"),
            () -> assertThat(info.totalAmount()).isEqualTo(2000L),
            () -> assertThat(info.items()).hasSize(1)
        );
    }

    @DisplayName("유저가 존재하지 않으면, 예외가 전파되고 주문은 생성되지 않는다.")
    @Test
    void throwsNotFound_whenUserMissing() {
        // arrange
        when(userService.getUser("ghost")).thenThrow(new CoreException(ErrorType.NOT_FOUND, "회원 없음"));

        // act
        CoreException ex = assertThrows(CoreException.class,
            () -> orderFacade.createOrder("ghost", List.of(new OrderLine(11L, 2))));

        // assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        verify(orderService, never()).createOrder(any(), any());
    }
}
