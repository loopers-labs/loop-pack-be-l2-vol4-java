package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.Month;

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
        public void passwordEncryptSuccess() {
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
}
