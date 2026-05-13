package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserV1Controller.class)
class UserV1ControllerTest {

    private static final String SIGN_UP_ENDPOINT = "/api/v1/users/signup";
    private static final String VALID_LOGIN_ID = "chanhee";
    private static final String VALID_RAW_PASSWORD = "chan1234!";
    private static final String VALID_NAME = "김찬희";
    private static final String VALID_BIRTH_DATE = "1995-05-10";
    private static final String VALID_EMAIL = "chan950510@gmail.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserFacade userFacade;

    @DisplayName("POST /api/v1/users/signup 요청 시")
    @Nested
    class SignUp {

        @DisplayName("유효한 가입 정보가 들어오면 201 Created 응답을 반환")
        @Test
        void returns201Created_whenRequestIsValid() throws Exception {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            given(userFacade.signUp(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act & assert
            mockMvc.perform(post(SIGN_UP_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        }

        @DisplayName("UserFacade에서 CONFLICT 예외가 발생하면 409 Conflict와 에러 메시지를 반환")
        @Test
        void returns409Conflict_whenUserFacadeThrowsConflict() throws Exception {
            // arrange
            String errorMessage = "[loginId = " + VALID_LOGIN_ID + "] 이미 사용 중인 ID 입니다.";
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            willThrow(new CoreException(ErrorType.CONFLICT, errorMessage))
                .given(userFacade).signUp(anyString(), anyString(), anyString(), anyString(), anyString());

            // act & assert
            mockMvc.perform(post(SIGN_UP_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.CONFLICT.getCode()))
                .andExpect(jsonPath("$.meta.message").value(errorMessage));
        }
    }

    @DisplayName("GET /api/v1/users/me 요청 시")
    @Nested
    class GetMyInfo {

        private static final String MY_INFO_ENDPOINT = "/api/v1/users/me";
        private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
        private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

        @DisplayName("올바른 인증 헤더면 200 OK 와 마스킹된 이름을 반환한다.")
        @Test
        void returns200WithMaskedName_whenAuthHeadersAreValid() throws Exception {
            // arrange
            given(userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act & assert
            mockMvc.perform(get(MY_INFO_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, VALID_RAW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.loginId").value(VALID_LOGIN_ID))
                .andExpect(jsonPath("$.data.name").value("김찬*"))
                .andExpect(jsonPath("$.data.birthDate").value(VALID_BIRTH_DATE))
                .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
        }

        @DisplayName("인증 헤더가 누락되면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returns401_whenAuthHeadersAreMissing() throws Exception {
            // act & assert
            mockMvc.perform(get(MY_INFO_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.UNAUTHORIZED.getCode()));
        }

        @DisplayName("잘못된 인증 정보면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returns401_whenCredentialsAreInvalid() throws Exception {
            // arrange
            String wrongPassword = "wrong1234!";
            willThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."))
                .given(userFacade).authenticate(VALID_LOGIN_ID, wrongPassword);

            // act & assert
            mockMvc.perform(get(MY_INFO_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.UNAUTHORIZED.getCode()));
        }
    }

    @DisplayName("PUT /api/v1/users/me/password 요청 시")
    @Nested
    class UpdatePassword {

        private static final String UPDATE_ENDPOINT = "/api/v1/users/me/password";
        private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
        private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
        private static final String NEW_RAW_PASSWORD = "newPw5678!";

        @DisplayName("유효한 요청이면 200 OK 응답을 반환한다.")
        @Test
        void returns200_whenRequestIsValid() throws Exception {
            // arrange
            given(userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(VALID_RAW_PASSWORD, NEW_RAW_PASSWORD);

            // act & assert
            mockMvc.perform(put(UPDATE_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, VALID_RAW_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returns401_whenOldPasswordDoesNotMatch() throws Exception {
            // arrange
            String wrongOldPassword = "wrong1234!";
            given(userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(wrongOldPassword, NEW_RAW_PASSWORD);
            willThrow(new CoreException(ErrorType.UNAUTHORIZED, "기존 비밀번호가 일치하지 않습니다."))
                .given(userFacade).changePassword(VALID_LOGIN_ID, wrongOldPassword, NEW_RAW_PASSWORD);

            // act & assert
            mockMvc.perform(put(UPDATE_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, VALID_RAW_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.UNAUTHORIZED.getCode()));
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returns400_whenNewPasswordEqualsCurrent() throws Exception {
            // arrange
            given(userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(VALID_RAW_PASSWORD, VALID_RAW_PASSWORD);
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 같을 수 없습니다."))
                .given(userFacade).changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_RAW_PASSWORD);

            // act & assert
            mockMvc.perform(put(UPDATE_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, VALID_RAW_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.BAD_REQUEST.getCode()));
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returns400_whenNewPasswordContainsBirthDate() throws Exception {
            // arrange
            String newPasswordWithBirthDate = "Pass19950510!";
            given(userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD))
                .willReturn(new UserInfo(VALID_LOGIN_ID, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(VALID_RAW_PASSWORD, newPasswordWithBirthDate);
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다."))
                .given(userFacade).changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, newPasswordWithBirthDate);

            // act & assert
            mockMvc.perform(put(UPDATE_ENDPOINT)
                    .header(LOGIN_ID_HEADER, VALID_LOGIN_ID)
                    .header(LOGIN_PW_HEADER, VALID_RAW_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.BAD_REQUEST.getCode()));
        }
    }
}
