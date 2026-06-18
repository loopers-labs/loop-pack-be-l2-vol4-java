package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
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

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserFacade userFacade;

    @Test
    @DisplayName("?뚯썝媛???붿껌 ??200 OK瑜?諛섑솚?섍퀬 Facade瑜??몄텧?쒕떎.")
    void signUp_ShouldReturnOkAndCallFacade() throws Exception {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("loginId", "tester01");
        request.put("password", "Password123!");
        request.put("name", "?뚯뒪??);
        request.put("birthDate", LocalDate.of(1990, 1, 1).toString());
        request.put("email", "tester01@example.com");

        // when & then
        mockMvc.perform(post("/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userFacade).signUp(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("???뺣낫 議고쉶 ??200 OK? ?뚯썝 ?뺣낫瑜?諛섑솚?쒕떎.")
    void getMyInfo_ShouldReturnOkAndUserInfo() throws Exception {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        UserInfo response = new UserInfo(
                "tester01",
                "?뚯뒪*",
                LocalDate.of(1990, 1, 1),
                "tester01@example.com"
        );
        given(userFacade.getMyInfo(loginId, password)).willReturn(response);

        // when & then
        mockMvc.perform(get("/v1/users/me")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("tester01"))
                .andExpect(jsonPath("$.data.name").value("?뚯뒪*"))
                .andExpect(jsonPath("$.data.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.data.email").value("tester01@example.com"));

        verify(userFacade).getMyInfo(loginId, password);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇 ?섏젙 ?붿껌 ??200 OK瑜?諛섑솚?섍퀬 Facade瑜??몄텧?쒕떎.")
    void updatePassword_ShouldReturnOkAndCallFacade() throws Exception {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        Map<String, Object> request = new HashMap<>();
        request.put("oldPassword", "OldPassword123!");
        request.put("newPassword", "NewPassword123!");

        // when & then
        mockMvc.perform(patch("/v1/users/me/password")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userFacade).updatePassword(eq(loginId), eq(password), any(), any());
    }
}
