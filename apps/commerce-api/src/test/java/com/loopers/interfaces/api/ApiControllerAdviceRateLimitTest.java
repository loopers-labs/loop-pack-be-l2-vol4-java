package com.loopers.interfaces.api;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.order.OrderV1Controller;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.support.error.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RateLimiter(8.2)가 문턱값을 초과한 호출을 거부하면 ApiControllerAdvice가 429 + TOO_MANY_REQUESTS로
// 변환하는지 OrderV1Controller를 통해 확인한다. 실제로 150 TPS를 쏟아붓는 대신 Facade가
// RequestNotPermitted를 던지도록 만들어, 웹 계층만 슬라이스로 검증한다.
@WebMvcTest(OrderV1Controller.class)
class ApiControllerAdviceRateLimitTest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";
    private static final String ENTRY_TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderFacade orderFacade;

    @DisplayName("RateLimiter 문턱값을 초과한 호출이면 429 Too Many Requests와 TOO_MANY_REQUESTS 에러 코드를 반환한다.")
    @Test
    void returnsTooManyRequests_whenRateLimiterRejectsCall() throws Exception {
        // given
        List<OrderFacade.OrderItemDto> items = List.of(new OrderFacade.OrderItemDto(1L, 1L));
        RateLimiter rateLimiter = RateLimiter.ofDefaults("orderCreate");
        given(orderFacade.createOrder(LOGIN_ID, LOGIN_PW, ENTRY_TOKEN, items, null))
                .willThrow(RequestNotPermitted.createRequestNotPermitted(rateLimiter));

        OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(1L, 1L)), null
        );

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", LOGIN_PW)
                        .header("X-Loopers-EntryToken", ENTRY_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.TOO_MANY_REQUESTS.getCode()));
    }
}
