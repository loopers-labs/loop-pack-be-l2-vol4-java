package com.loopers.application.order;

import com.loopers.domain.order.OrderAdminService;
import com.loopers.domain.order.OrderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAdminFacadeTest {

    @InjectMocks
    private OrderAdminFacade orderAdminFacade;

    @Mock
    private OrderAdminService orderAdminService;

    @Test
    @DisplayName("?꾩껜 二쇰Ц 紐⑸줉 議고쉶 ?붿껌 ???대뱶誘??쒕퉬?ㅼ쓽 議고쉶 濡쒖쭅???몄텧?쒕떎.")
    void getAllOrders_ShouldCallService() {
        // given
        given(orderAdminService.getAllOrders()).willReturn(List.of(new OrderModel(1L)));

        // when
        List<OrderModel> result = orderAdminFacade.getAllOrders();

        // then
        assertThat(result).hasSize(1);
        verify(orderAdminService).getAllOrders();
    }

    @Test
    @DisplayName("?④굔 二쇰Ц ?곸꽭 議고쉶 ?붿껌 ???대뱶誘??쒕퉬?ㅼ쓽 議고쉶 濡쒖쭅???몄텧?쒕떎.")
    void getOrder_ShouldCallService() {
        // given
        Long orderId = 100L;
        given(orderAdminService.getOrder(orderId)).willReturn(new OrderModel(1L));

        // when
        orderAdminFacade.getOrder(orderId);

        // then
        verify(orderAdminService).getOrder(orderId);
    }
}
