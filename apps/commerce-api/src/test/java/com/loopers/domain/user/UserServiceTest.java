package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @DisplayName("유저의 id로 유저를 조회할 때")
    @Nested
    class ReadSingular {

        private long existSequence = 0L;
        private UserModel userModel = null;

        @BeforeEach
        public void init() {
            String loginId = "saved";
            String name = "tester";
            BirthVO localDate = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            PasswordVO password = new PasswordVO("test_1234");
            EmailVO email = new EmailVO("test@tester.com");

            UserModel userModel = inMemoryUserRepository.save(UserModel.of(loginId, name, localDate, password, email));

            this.userModel = userModel;
            this.existSequence = userModel.getId();
        }

        @DisplayName("존재하지 않는 ID의 경우에 실패한다.")
        @Test
        public void getUserFailureTest() {
            // given
            Long userSequenceId = 2L;

            // when then
            assertThatThrownBy(() -> userService.getUserModel(userSequenceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유저의 아이디가 존재하지 않습니다.");
        }

        @DisplayName("존재하는 유저의 ID의 경우에는 유저를 반환한다")
        @Test
        public void getUserSuccessTest() {
            // given
            Long userSequenceId = existSequence;

            // when
            UserModel userModel = userService.getUserModel(userSequenceId);

            // then
            Assertions.assertNotNull(userModel);
            Assertions.assertEquals(existSequence, userModel.getId());
        }
    }
}
