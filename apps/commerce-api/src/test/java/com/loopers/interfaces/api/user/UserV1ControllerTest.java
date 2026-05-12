package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserFacade;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
