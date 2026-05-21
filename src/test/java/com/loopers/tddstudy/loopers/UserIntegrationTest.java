package com.loopers.tddstudy.loopers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.dto.SignUpRequest;
import com.loopers.tddstudy.dto.ChangePasswordRequest;
import com.loopers.tddstudy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class UserIntegrationTest {

        @Autowired
        private  MockMvc mockMvc;

        @Autowired
        private  ObjectMapper objectMapper;

        @Autowired
        private  UserRepository userRepository;

        @BeforeEach
        void setUp(){
            userRepository.deleteAll();
        }

        @Test
        @DisplayName("회원가입 후 로그인성공")
        void signUpAndLogin_success() throws Exception{
            //회원가입
            SignUpRequest signUpRequest = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

            mockMvc.perform(post("/api/users/sign-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signUpRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("lilpa123"))
                    .andExpect(jsonPath("loginPw").doesNotExist());

            //로그인
            LoginRequest loginRequest = new LoginRequest("lilpa123","Pass1234!");

            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("lilpa123"));

        }

        @Test
        @DisplayName("회원가입 후 회원 조회")
        void signUpAndGetUser_success() throws Exception {
            // 회원가입
            SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

            mockMvc.perform(post("/api/users/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 내 정보 조회
            mockMvc.perform(get("/api/users/me")
                            .header("X-Loopers-LoginId", "lilpa123")
                            .header("X-Loopers-LoginPw", "Pass1234!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("lilpa123"))
                    .andExpect(jsonPath("$.name").value("김릴*"))
                    .andExpect(jsonPath("$.loginPw").doesNotExist());

        }

        @Test
        @DisplayName("회원가입 후 패스워드 변경")
        void signUpAndChangePassword_success() throws Exception {
            // 회원가입
            SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

            mockMvc.perform(post("/api/users/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 비밀번호 변경
            ChangePasswordRequest changeRequest = new ChangePasswordRequest("NewPass1!");


            mockMvc.perform(patch("/api/users/me/password")
                            .header("X-Loopers-LoginId", "lilpa123")
                            .header("X-Loopers-LoginPw", "Pass1234!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isOk());

            // 변경된 비밀번호로 로그인
            LoginRequest loginRequest = new LoginRequest("lilpa123", "NewPass1!");

            mockMvc.perform(post("/api/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("중복 loginId 로 회원가입 시 400을 반환")
        void signUp_duplicateLoginId_returns400() throws Exception {
            SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

            mockMvc.perform(post("/api/users/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());


            // 같은 ID 로 재가입
            mockMvc.perform(post("/api/users/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

        }

}
