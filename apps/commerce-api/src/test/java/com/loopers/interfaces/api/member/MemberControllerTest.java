package com.loopers.interfaces.api.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberFacade memberFacade;

    @Test
    @DisplayName("회원가입 요청 시 200 OK를 반환하고 Facade를 호출한다.")
    void signUp_ShouldReturnOkAndCallFacade() throws Exception {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("loginId", "tester01");
        request.put("password", "Password123!");
        request.put("name", "테스터");
        request.put("birthDate", LocalDate.of(1990, 1, 1).toString());
        request.put("email", "tester01@example.com");

        // when & then
        mockMvc.perform(post("/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(memberFacade).signUp(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("내 정보 조회 시 200 OK와 회원 정보를 반환한다.")
    void getMyInfo_ShouldReturnOkAndMemberInfo() throws Exception {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        MemberInfo response = new MemberInfo(
                "tester01",
                "테스*",
                LocalDate.of(1990, 1, 1),
                "tester01@example.com"
        );
        given(memberFacade.getMyInfo(loginId, password)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/members/me")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("tester01"))
                .andExpect(jsonPath("$.data.name").value("테스*"))
                .andExpect(jsonPath("$.data.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.data.email").value("tester01@example.com"));

        verify(memberFacade).getMyInfo(loginId, password);
    }

    @Test
    @DisplayName("비밀번호 수정 요청 시 200 OK를 반환하고 Facade를 호출한다.")
    void updatePassword_ShouldReturnOkAndCallFacade() throws Exception {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        Map<String, Object> request = new HashMap<>();
        request.put("oldPassword", "OldPassword123!");
        request.put("newPassword", "NewPassword123!");

        // when & then
        mockMvc.perform(patch("/v1/members/me/password")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(memberFacade).updatePassword(eq(loginId), eq(password), any(), any());
    }
}
