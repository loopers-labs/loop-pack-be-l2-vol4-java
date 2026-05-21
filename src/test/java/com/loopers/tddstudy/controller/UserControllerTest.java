package com.loopers.tddstudy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.dto.SignUpRequest;
import com.loopers.tddstudy.service.UserService;
import com.loopers.tddstudy.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공 시 200")
    void signUp_success() throws Exception {
        SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userService.signUp(any(SignUpRequest.class))).thenReturn(user);

        mockMvc.perform(post("/api/users/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("lilpa123"));
    }

    @Test
    @DisplayName("회원정보 조회 성공 시 200")
    void getUser_success() throws Exception {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userService.login(any(LoginRequest.class))).thenReturn(user);

        mockMvc.perform(get("/api/users/me")
                        .header("X-Loopers-LoginId", "lilpa123")
                        .header("X-Loopers-LoginPw", "Pass1234!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("lilpa123"))
                .andExpect(jsonPath("$.name").value("김릴*"));
    }

    @Test
    @DisplayName("헤더 없이 회원정보 조회 시 400")
    void getUser_noHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인 시 400")
    void login_wrongPassword_returns400() throws Exception {
        LoginRequest request = new LoginRequest("lilpa123", "Wrong1234!");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호 변경 성공 시 200을 반환한다")
    void changePassword_success() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .header("X-Loopers-LoginId", "lilpa123")
                        .header("X-Loopers-LoginPw", "Pass1234!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("해더없이 요청시 400 반환")
    void changePassword_noHeader_returns400() throws Exception{
        mockMvc.perform(patch("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"lilPass1!\"}")
                ).andExpect(status().isBadRequest());


    }

    @Test
    @DisplayName("회우너가입 성공 시 비밀번호가 반한되지않음")
    void signUp_doesNotReturnPassword() throws  Exception{
        SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userService.signUp(any(SignUpRequest.class))).thenReturn(user);

        mockMvc.perform(post("/api/users/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginPw").doesNotExist());

    }

    @Test
    @DisplayName("로그인 성공시 비밀번호가 반환되지않음")
    void login_doesNotReturnPassword() throws  Exception{
        LoginRequest request = new LoginRequest("lilpa123", "Pass1234!");
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userService.login(any(LoginRequest.class))).thenReturn(user);

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginPw").doesNotExist());

    }


}
