package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class UserFacadeIntegrateTest {

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("유저를 생성할 때")
    @Nested
    class Create {

        @BeforeEach
        public void init() {
            String loginId = "duplicate";
            String name = "tester";
            BirthVO localDate = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            String password = "test_1234";
            EmailVO email = new EmailVO("test@tester.com");

            UserModel userModel = UserModel.of(loginId, name, password, localDate, email);

            userRepository.save(userModel);
        }

        @DisplayName("userId에 중복이 없는 경우 성공적으로 생성된다")
        @Test
        public void userCreateSuccessTest() {
            // given
            String loginId = "test";
            String name = "테스터";
            LocalDate birth = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";

            // when
            var result = userFacade.createUser(loginId, name, birth, password, email);
            
            // then
            assertNotNull(result);
            assertEquals(email, result.emailVO().email());
        }
        
        @DisplayName("중복인 userId가 있는 경우 실패한다")
        @Test
        public void userCreateFailureTest() {
            // given
            String loginId = "duplicate";
            String name = "테스터";
            LocalDate birth = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";
            
            // when then
            assertThatThrownBy(() -> userFacade.createUser(loginId, name, birth, password, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(ErrorType.CONFLICT.getMessage());
        }
        
        @DisplayName("비밀번호는 암호화 된다")
        @Test
        public void passwordEncryptSuccessTest() {
            // given
            String loginId = "encrypted";
            String name = "테스터";
            LocalDate birth = LocalDate.of(1993, Month.MARCH, 16);
            String password = "test_1234";
            String email = "test@tester.com";

            // when
            userFacade.createUser(loginId, name, birth, password, email);
            
            // then
            UserModel saved = userRepository.findByLoginId(loginId).get();
            assertThat(bCryptPasswordEncoder.matches(password, saved.getPassword())).isTrue();
        }
    }

    @Nested
    @DisplayName("유저를 조회할 때")
    class Read {

        private long expectedId = 0L;

        @BeforeEach
        public void init() {
            String loginId = "encrypted";
            String name = "테스터";
            String password = "test_1234";
            LocalDate birth = LocalDate.of(1993, Month.MARCH, 16);
            String email = "test@tester.com";

            UserModel savedUser = userRepository.save(UserModel.of(loginId, name, password, new BirthVO(birth), new EmailVO(email)));
            expectedId = savedUser.getId();
        }

        @DisplayName("유효한 id로 유저를 조회하면 UserInfo를 반환한다")
        @Test
        public void getUserInfoSuccessTest() {
            // given
            String expected = "encrypted";

            // when
            UserInfo userInfo = userFacade.getUserInfo(expectedId);
            
            // then
            Assertions.assertThat(userInfo).isInstanceOf(UserInfo.class);
            Assertions.assertThat(userInfo.loginId()).isEqualTo(expected);
        }

        @DisplayName("유효하지 않은 id로 유저를 조회하면")
        @Test
        public void getUserInfoFailureTest() {
            // given
            long invalidId = 999_999L;

            // when then
            assertThatThrownBy(() -> userFacade.getUserInfo(invalidId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유저의 아이디가 존재하지 않습니다.");
        }
    }

    @DisplayName("유저의 정보를 변경할 때")
    @Nested
    class Update {

        private long expectedId = 0L;
        private String currentPassword = null;

        @BeforeEach
        public void init() {
            String loginId = "encrypted";
            String name = "테스터";
            String password = "test_1234";
            LocalDate birth = LocalDate.of(1993, Month.MARCH, 16);
            String email = "test@tester.com";

            UserModel savedUser = userRepository.save(UserModel.of(loginId, name, bCryptPasswordEncoder.encode("test_1234"), new BirthVO(birth), new EmailVO(email)));
            expectedId = savedUser.getId();
            currentPassword = password;
        }

        @DisplayName("현재비밀번호와 변경하려는 비밀번호를 받아서 변경시킨다")
        @Test
        public void changePasswordSuccessTest() {
            // given
            String target = "test_!!34";

            // when
            userFacade.changePassword(expectedId, currentPassword, target);
            UserModel userModel = userRepository.findById(expectedId).get();

            // then
            Assertions.assertThat(bCryptPasswordEncoder.matches(target, userModel.getPassword())).isTrue();
        }
        
        @DisplayName("파라미터로 받은 비밀번호가 규칙과 일치하지 않는다면")
        @TestFactory
        public Collection<DynamicTest> changePasswordFailureTest() {
            return List.of(
                    DynamicTest.dynamicTest("현재비밀번호와 파라미터로 받은 현재비밀번호가 일치하지 않으면 오류가 발생하다", () -> {
                        String wrongCurrentPassword = currentPassword + "!";
                        String target = "test_!!34";

                        Assertions.assertThatThrownBy(() -> userFacade.changePassword(expectedId, wrongCurrentPassword, target))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("비밀번호가 일치하지 않습니다.");

                    }),
                    DynamicTest.dynamicTest("현재비밀번호와 파라미터로 받은 변경비밀번호가 일치하면 오류가 발생하다", () -> {
                        String target = currentPassword;

                        Assertions.assertThatThrownBy(() -> userFacade.changePassword(expectedId, currentPassword, target))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("현재 비밀번호는 사용할 수 없습니다.");
                    })
            );
        }
    }
}
