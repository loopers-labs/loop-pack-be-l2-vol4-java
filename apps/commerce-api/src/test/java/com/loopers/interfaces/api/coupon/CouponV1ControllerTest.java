package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponV1Controller.class)
class CouponV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponFacade couponFacade;

    @Test
    @DisplayName("荑좏룿 諛쒓툒 ?붿껌 ??200 OK瑜?諛섑솚?섍퀬 Facade瑜??몄텧?쒕떎.")
    void issueCoupon_ShouldReturnOk() throws Exception {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        // when & then
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header("X-Loopers-UserId", userId))
                .andExpect(status().isOk());

        verify(couponFacade).issueCoupon(userId, couponId);
    }

    @Test
    @DisplayName("??荑좏룿 紐⑸줉 議고쉶 ??200 OK? 紐⑸줉??諛섑솚?쒕떎.")
    void getUsersCoupons_ShouldReturnOkAndList() throws Exception {
        // given
        Long userId = 1L;
        List<CouponV1Dto.UserCouponResponse> mockResponse = List.of(
                new CouponV1Dto.UserCouponResponse(
                        100L, 10L, "10%?좎씤", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, null, "AVAILABLE", LocalDateTime.now().plusDays(1)
                )
        );
        given(couponFacade.getUsersCoupons(userId)).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/coupons")
                        .header("X-Loopers-UserId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100L))
                .andExpect(jsonPath("$.data[0].name").value("10%?좎씤"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));

        verify(couponFacade).getUsersCoupons(userId);
    }
}
