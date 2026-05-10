package com.loopers.domain.user;

import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private final InMemoryUserRepository inMemoryUserRepository = new InMemoryUserRepository();
    private final UserService userService = new UserService(inMemoryUserRepository);

    @DisplayName("유저를 생성할 때")
    @Nested
    public class Create {
        @BeforeEach
        public void init() {
            String loginId = "duplicate";
            String name = "tester";
            LocalDate localDate = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";

            userService.createUserModel(loginId, name, localDate, password, email);
        }

        @DisplayName("중복되지 않은 유저라면 저장에 성공한다.")
        @Test
        public void saveSuccess() {
            // given
            String loginId = "test";
            String name = "tester";
            LocalDate localDate = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";

            // when
            var result = userService.createUserModel(loginId, name, localDate, password, email);

            // then
            Assertions.assertNotNull(result);
            Assertions.assertEquals(loginId, result.getLoginId());
        }

        @DisplayName("중복된 유저는 저장에 실패한다.")
        @Test
        public void saveFailure() {
            // given
            String loginId = "duplicate";
            String name = "tester";
            LocalDate localDate = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";

            // when then
            assertThatThrownBy(() -> userService.createUserModel(loginId, name, localDate, password, email))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ErrorType.CONFLICT.getMessage());
        }
    }
}
