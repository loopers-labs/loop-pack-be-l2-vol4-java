package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.payment.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderV1Controller.class)
class OrderV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderFacade orderFacade;

    @Test
    @DisplayName("?듯빀 二쇰Ц 寃곗젣(checkout) ?붿껌 ??HTTP 200怨?orderId媛 諛섑솚?쒕떎.")
    void checkout_ApiSuccess() throws Exception {
        // given
        Long userId = 1L;
        OrderV1Dto.CheckoutRequest request = new OrderV1Dto.CheckoutRequest(
                List.of(new OrderV1Dto.ItemRequest(10L, 2)),
                42L,
                PaymentMethod.CARD
        );

        given(orderFacade.checkout(eq(userId), any())).willReturn(100L);

        // when & then
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("X-Loopers-UserId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderId").value(100));
    }
}
