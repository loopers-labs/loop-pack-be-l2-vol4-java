package com.loopers.interfaces.api.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.domain.coupon.CouponType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponAdminController.class)
class CouponAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CouponAdminFacade couponAdminFacade;

    @Test
    @DisplayName("?쒗뵆由??깅줉 ?깃났 ??200 OK瑜?諛섑솚?섍퀬 Facade瑜??몄텧?쒕떎.")
    void registerTemplate_ShouldReturnOk() throws Exception {
        // given
        CouponAdminDto.RegisterTemplateRequest request = new CouponAdminDto.RegisterTemplateRequest(
                "?뚯뒪?몄퓼??, CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10)
        );
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                1L, "?뚯뒪?몄퓼??, CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, request.expiredAt()
        );
        given(couponAdminFacade.registerTemplate(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api-admin/v1/coupons")
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("?뚯뒪?몄퓼??));

        verify(couponAdminFacade).registerTemplate(any());
    }

    @Test
    @DisplayName("?쒗뵆由??④굔 議고쉶 ??200 OK? ?곸꽭 ?댁슜??諛섑솚?쒕떎.")
    void getTemplate_ShouldReturnOkAndDetails() throws Exception {
        // given
        Long id = 1L;
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                id, "?뚯뒪?몄퓼??, CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10)
        );
        given(couponAdminFacade.getTemplate(id)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", id)
                        .header("X-Loopers-Ldap", "loopers.admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("?뚯뒪?몄퓼??));

        verify(couponAdminFacade).getTemplate(id);
    }

    @Test
    @DisplayName("?쒗뵆由?紐⑸줉 議고쉶 ??200 OK? ?섏씠吏?泥섎━??紐⑸줉??諛섑솚?쒕떎.")
    void getTemplates_ShouldReturnOkAndPagedList() throws Exception {
        // given
        Page<CouponAdminDto.TemplateResponse> responsePage = new PageImpl<>(List.of(
                new CouponAdminDto.TemplateResponse(1L, "荑좏룿1", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        ), PageRequest.of(0, 20), 1);
        given(couponAdminFacade.getTemplates(any())).willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons")
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("荑좏룿1"));

        verify(couponAdminFacade).getTemplates(any());
    }

    @Test
    @DisplayName("?쒗뵆由??섏젙 ??200 OK? ?섏젙???댁슜??諛섑솚?쒕떎.")
    void updateTemplate_ShouldReturnOkAndUpdatedDetails() throws Exception {
        // given
        Long id = 1L;
        CouponAdminDto.UpdateTemplateRequest request = new CouponAdminDto.UpdateTemplateRequest(
                "?섏젙荑좏룿", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5000"), LocalDateTime.now().plusDays(10)
        );
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                id, "?섏젙荑좏룿", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5000"), request.expiredAt()
        );
        given(couponAdminFacade.updateTemplate(eq(id), any())).willReturn(response);

        // when & then
        mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", id)
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("?섏젙荑좏룿"));

        verify(couponAdminFacade).updateTemplate(eq(id), any());
    }

    @Test
    @DisplayName("?쒗뵆由???젣 ??200 OK瑜?諛섑솚?섍퀬 Facade瑜??몄텧?쒕떎.")
    void deleteTemplate_ShouldReturnOk() throws Exception {
        // given
        Long id = 1L;

        // when & then
        mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", id)
                        .header("X-Loopers-Ldap", "loopers.admin"))
                .andExpect(status().isOk());

        verify(couponAdminFacade).deleteTemplate(id);
    }

    @Test
    @DisplayName("?뱀젙 荑좏룿??諛쒓툒 ?댁뿭 議고쉶 ??200 OK? ?섏씠吏?泥섎━??紐⑸줉??諛섑솚?쒕떎.")
    void getIssues_ShouldReturnOkAndPagedList() throws Exception {
        // given
        Long couponId = 1L;
        Page<CouponAdminDto.IssueResponse> responsePage = new PageImpl<>(List.of(
                new CouponAdminDto.IssueResponse(100L, 1L, couponId, "AVAILABLE", java.time.ZonedDateTime.now())
        ), PageRequest.of(0, 20), 1);
        given(couponAdminFacade.getIssues(eq(couponId), any())).willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId)
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(100L))
                .andExpect(jsonPath("$.data.content[0].status").value("AVAILABLE"));

        verify(couponAdminFacade).getIssues(eq(couponId), any());
    }
}
