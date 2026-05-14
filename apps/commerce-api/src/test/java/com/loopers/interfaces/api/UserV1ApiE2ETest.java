package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class UserV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/users";
    private static final String VALID_LOGIN_ID = "minbo";
    private static final String VALID_PASSWORD = "Test1234!";
    private static final String VALID_NAME = "민보";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1991, 8, 21);
    private static final String VALID_EMAIL = "test@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class CreateUser {

        @DisplayName("유효한 요청이면, 회원이 생성되고 사용자 정보를 반환한다.")
        @Test
        void createsUser_whenValidRequest() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.data().loginId()).isEqualTo(VALID_LOGIN_ID),
                    () -> assertThat(response.data().name()).isEqualTo(VALID_NAME),
                    () -> assertThat(response.data().email()).isEqualTo(VALID_EMAIL),
                    () -> assertThat(userRepository.existsByLoginId(VALID_LOGIN_ID)).isTrue()
            );
        }

        @DisplayName("이미 사용중인 loginId로 요청하면, CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            mockMvc.perform(post(ENDPOINT)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(request)))
                   .andReturn();

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("loginId 가 영문/숫자 외 문자를 포함하면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLoginIdContainsInvalidChars() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    "한글ID", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("비밀번호 형식이 잘못되면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordFormatInvalid() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, "1234", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("이메일 형식이 잘못되면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenEmailFormatInvalid() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "잘못된이메일"
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDate() throws Exception {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, "P20000101!", VALID_NAME, LocalDate.of(2000, 1, 1), VALID_EMAIL
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("PATCH /api/v1/users/me")
    @Nested
    class ChangePassword {

        private static final String CHANGE_ENDPOINT = ENDPOINT + "/me";
        private static final String NEW_PASSWORD = "NewPass5678!";

        private void signUp() throws Exception {
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                    VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            mockMvc.perform(post(ENDPOINT)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(request)))
                   .andReturn();
        }

        @DisplayName("유효한 헤더와 요청이면, 비밀번호가 변경되고 OK 응답을 받는다.")
        @Test
        void changesPassword_whenValidRequest() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, NEW_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, VALID_LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, VALID_PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.data().loginId()).isEqualTo(VALID_LOGIN_ID)
            );
        }

        @DisplayName("인증 헤더가 없으면, UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeadersMissing() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, NEW_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("헤더의 비밀번호가 틀리면, UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeaderPasswordInvalid() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, NEW_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, VALID_LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, "WrongPass!")
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("헤더의 loginId가 존재하지 않으면, UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeaderLoginIdNotFound() throws Exception {
            // given
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, NEW_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, "nonexistent")
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, VALID_PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenBodyCurrentPasswordMismatch() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    "WrongPass!", NEW_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, VALID_LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, VALID_PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordSameAsCurrent() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, VALID_PASSWORD
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, VALID_LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, VALID_PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("새 비밀번호 형식이 잘못되면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordFormatInvalid() throws Exception {
            // given
            signUp();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                    VALID_PASSWORD, "1234"
            );

            // when
            MvcResult mvcResult = mockMvc.perform(patch(CHANGE_ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, VALID_LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, VALID_PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();
            ApiResponse<UserV1Dto.UserResponse> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
