package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.Month;

class UserServiceTest {

    private final InMemoryUserRepository inMemoryUserRepository = new InMemoryUserRepository();
    private final UserService userService = new UserService(inMemoryUserRepository);

    @DisplayName("유저의 정보를 받아서 유저를 저장한다.")
    @Test
    public void saveSuccess() {
        // given
        String loginId = "test";
        String name = "tester";
        BirthVO localDate = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
        PasswordVO password = new PasswordVO("test_1234");
        EmailVO email = new EmailVO("test@tester.com");

        // when
        var result = userService.createUserModel(loginId, name, localDate, password, email);

        // then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(loginId, result.getLoginId());
    }

    @DisplayName("유저의 중복체크를 할 때")
    @Nested
    class Duplicate {

        @BeforeEach
        public void init() {
            String loginId = "duplicate";
            String name = "tester";
            BirthVO localDate = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            PasswordVO password = new PasswordVO("test_1234");
            EmailVO email = new EmailVO("test@tester.com");

            UserModel userModel = UserModel.of(loginId, name, localDate, password, email);

            inMemoryUserRepository.save(userModel);
        }

        @DisplayName("중복된 유저는 True를 반환한다.")
        @Test
        public void duplicate() {
            // given
            String loginId = "duplicate";

            // when
            boolean result = userService.checkLoginIdDuplication(loginId);

            // then
            Assertions.assertTrue(result);
        }

        @DisplayName("중복 되지 않은 유저는 false를 반환한다.")
        @Test
        public void notDuplicate() {
            // given
            String loginId = "tester_new";

            // when
            boolean result = userService.checkLoginIdDuplication(loginId);

            // then
            Assertions.assertFalse(result);
        }
    }
}
