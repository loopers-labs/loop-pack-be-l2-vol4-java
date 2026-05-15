package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.CoreException;
import fixture.UserModelFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private final InMemoryUserRepository inMemoryUserRepository = new InMemoryUserRepository();
    private final UserService userService = new UserService(inMemoryUserRepository);

    @DisplayName("유저의 정보를 받아서 유저를 저장한다.")
    @Test
    public void saveSuccess() {
        // given
        UserModelFixture defaults = UserModelFixture.defaults();

        // when
        var result = userService.createUserModel(defaults.loginId(), defaults.name(), defaults.password(), new BirthVO(defaults.birth()), new EmailVO(defaults.email()));

        // then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(defaults.loginId(), result.getLoginId());
    }

    @DisplayName("유저의 중복체크를 할 때")
    @Nested
    class Duplicate {

        private String expectedId = null;

        @BeforeEach
        public void init() {
            UserModelFixture duplicate = UserModelFixture.duplicate();
            UserModel userModel = UserModel.of(duplicate.loginId(), duplicate.name(), duplicate.password(), new BirthVO(duplicate.birth()), new EmailVO(duplicate.email()));

            expectedId = duplicate.loginId();
            inMemoryUserRepository.save(userModel);
        }

        @DisplayName("중복된 유저는 True를 반환한다.")
        @Test
        public void duplicate() {
            // given
            String loginId = expectedId;

            // when then
            assertThatThrownBy(() -> userService.checkLoginIdDuplication(loginId))
                    .isInstanceOf(CoreException.class)
                    .hasMessage("이미 존재하는 유저의 아이디입니다.");
        }

        @DisplayName("중복 되지 않은 유저는 통과한다.")
        @Test
        public void notDuplicate() {
            // given
            String loginId = "tester_new";

            // when then
            userService.checkLoginIdDuplication(loginId);
        }
    }

    @DisplayName("유저의 id로 유저를 조회할 때")
    @Nested
    class ReadSingular {

        private long existSequence = 0L;
        private UserModel userModel = null;

        @BeforeEach
        public void init() {
            UserModelFixture defaults = UserModelFixture.defaults();
            UserModel userModel = inMemoryUserRepository.save(
                UserModel.of(defaults.loginId(), defaults.name(), defaults.password(), new BirthVO(defaults.birth()), new EmailVO(defaults.email()))
            );

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
                    .isInstanceOf(CoreException.class)
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
