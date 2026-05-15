package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserAccountFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.WebMvcConfig;
import com.loopers.interfaces.api.user.UserV1Controller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserV1Controller.class)
@Import({WebMvcConfig.class, AuthInterceptor.class, CurrentUserArgumentResolver.class})
class AuthInterceptorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountFacade userFacade;

    @MockitoBean
    private UserService userService;

    @DisplayName("GET /api/v1/users/me 요청 시,")
    @Nested
    class GetMe {

        @DisplayName("Given 인증 헤더 누락 / When 요청 / Then 401이 반환된다.")
        @Test
        void returns401_whenAuthHeadersAreMissing() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        }

        @DisplayName("Given 유효한 인증 헤더 / When 요청 / Then 200이 반환된다.")
        @Test
        void returns200_whenValidCredentialsProvided() throws Exception {
            // arrange
            UserModel user = new UserModel("user123", "encoded!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            UserInfo info = new UserInfo(1L, "user123", "홍길*", LocalDate.of(1990, 1, 15), "test@example.com");

            given(userService.authenticate(eq("user123"), eq("Password1!"))).willReturn(user);
            given(userFacade.getMe(any(UserModel.class))).willReturn(info);

            // act & assert
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-Loopers-LoginId", "user123")
                    .header("X-Loopers-LoginPw", "Password1!"))
                .andExpect(status().isOk());
        }
    }
}
