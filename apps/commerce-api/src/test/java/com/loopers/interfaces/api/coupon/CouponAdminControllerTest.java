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
    @DisplayName("템플릿 등록 성공 시 200 OK를 반환하고 Facade를 호출한다.")
    void registerTemplate_ShouldReturnOk() throws Exception {
        // given
        CouponAdminDto.RegisterTemplateRequest request = new CouponAdminDto.RegisterTemplateRequest(
                "테스트쿠폰", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10)
        );
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                1L, "테스트쿠폰", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, request.expiredAt()
        );
        given(couponAdminFacade.registerTemplate(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api-admin/v1/coupons")
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("테스트쿠폰"));

        verify(couponAdminFacade).registerTemplate(any());
    }

    @Test
    @DisplayName("템플릿 단건 조회 시 200 OK와 상세 내용을 반환한다.")
    void getTemplate_ShouldReturnOkAndDetails() throws Exception {
        // given
        Long id = 1L;
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                id, "테스트쿠폰", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10)
        );
        given(couponAdminFacade.getTemplate(id)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", id)
                        .header("X-Loopers-Ldap", "loopers.admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("테스트쿠폰"));

        verify(couponAdminFacade).getTemplate(id);
    }

    @Test
    @DisplayName("템플릿 목록 조회 시 200 OK와 페이징 처리된 목록을 반환한다.")
    void getTemplates_ShouldReturnOkAndPagedList() throws Exception {
        // given
        Page<CouponAdminDto.TemplateResponse> responsePage = new PageImpl<>(List.of(
                new CouponAdminDto.TemplateResponse(1L, "쿠폰1", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(10))
        ), PageRequest.of(0, 20), 1);
        given(couponAdminFacade.getTemplates(any())).willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons")
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("쿠폰1"));

        verify(couponAdminFacade).getTemplates(any());
    }

    @Test
    @DisplayName("템플릿 수정 시 200 OK와 수정된 내용을 반환한다.")
    void updateTemplate_ShouldReturnOkAndUpdatedDetails() throws Exception {
        // given
        Long id = 1L;
        CouponAdminDto.UpdateTemplateRequest request = new CouponAdminDto.UpdateTemplateRequest(
                "수정쿠폰", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5000"), LocalDateTime.now().plusDays(10)
        );
        CouponAdminDto.TemplateResponse response = new CouponAdminDto.TemplateResponse(
                id, "수정쿠폰", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5000"), request.expiredAt()
        );
        given(couponAdminFacade.updateTemplate(eq(id), any())).willReturn(response);

        // when & then
        mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", id)
                        .header("X-Loopers-Ldap", "loopers.admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정쿠폰"));

        verify(couponAdminFacade).updateTemplate(eq(id), any());
    }

    @Test
    @DisplayName("템플릿 삭제 시 200 OK를 반환하고 Facade를 호출한다.")
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
    @DisplayName("특정 쿠폰의 발급 내역 조회 시 200 OK와 페이징 처리된 목록을 반환한다.")
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
